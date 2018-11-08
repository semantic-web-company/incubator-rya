package org.apache.rya.rdftriplestore.evaluation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.apache.rya.api.RdfCloudTripleStoreConfiguration;
import org.apache.rya.api.RdfCloudTripleStoreUtils;
import org.apache.rya.api.utils.NullableStatementImpl;
import org.apache.rya.rdftriplestore.RdfCloudTripleStoreConnection;
import org.apache.rya.rdftriplestore.RdfCloudTripleStoreConnection.StoreTripleSource;
import org.apache.rya.rdftriplestore.inference.InferenceEngine;
import org.apache.rya.rdftriplestore.inference.InferenceEngineException;
import org.apache.rya.rdftriplestore.utils.FixedStatementPattern;
import org.apache.rya.rdftriplestore.utils.TransitivePropertySP;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.ConvertingIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.common.iteration.Iteration;
import org.eclipse.rdf4j.common.iteration.IteratorIteration;
import org.eclipse.rdf4j.common.iteration.LimitIteration;
import org.eclipse.rdf4j.common.iteration.OffsetIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.Filter;
import org.eclipse.rdf4j.query.algebra.Join;
import org.eclipse.rdf4j.query.algebra.QueryRoot;
import org.eclipse.rdf4j.query.algebra.Slice;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy;
import org.eclipse.rdf4j.query.algebra.evaluation.iterator.FilterIterator;
import org.eclipse.rdf4j.query.algebra.evaluation.iterator.JoinIterator;

import com.google.common.collect.Lists;
import org.eclipse.rdf4j.query.impl.MapBindingSet;

/**
 */
public class ParallelEvaluationStrategyImpl extends StrictEvaluationStrategy {
    private static Logger logger = Logger.getLogger(ParallelEvaluationStrategyImpl.class);
    
    private int numOfThreads = 10;
    private boolean performant = true;
    private boolean displayQueryPlan = false;
    private ExecutorService executorService;
    private InferenceEngine inferenceEngine;

    public ParallelEvaluationStrategyImpl(StoreTripleSource tripleSource, InferenceEngine inferenceEngine,
                                          Dataset dataset, RdfCloudTripleStoreConfiguration conf) {
        super(tripleSource, dataset, null);
        Integer nthreads = conf.getNumThreads();
        this.numOfThreads = 1;
        Boolean val = conf.isPerformant();
        this.performant = (val != null) ? val : this.performant;
        val = conf.isDisplayQueryPlan();
        this.displayQueryPlan = (val != null) ? val : this.displayQueryPlan;
        this.executorService = Executors.newFixedThreadPool(this.numOfThreads);
        this.inferenceEngine = inferenceEngine;
    }

    @Override
    public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(Join join, BindingSet bindings) throws QueryEvaluationException {
        if (performant) {
            TupleExpr buffer = join.getLeftArg();
            if (join.getRightArg() instanceof StatementPattern) {
                TupleExpr stmtPat = join.getRightArg();
//                if(buffer instanceof StatementPattern && !(stmtPat instanceof StatementPattern)){
//                    buffer = stmtPat;
//                    stmtPat = join.getLeftArg();
//                }

                return new MultipleBindingSetsIterator(this, buffer, (StatementPattern) stmtPat, bindings);
            } else if (join.getRightArg() instanceof ExternalBatchingIterator) {
                    TupleExpr stmtPat = join.getRightArg();

                    return new ExternalMultipleBindingSetsIterator(this, buffer, (ExternalBatchingIterator) stmtPat, bindings);
            } else if (join.getRightArg() instanceof Filter) {
                //add performance for the filter too
                Filter filter = (Filter) join.getRightArg();
                TupleExpr filterChild = filter.getArg();
                if (filterChild instanceof StatementPattern) {
                    return new FilterIterator(filter, new MultipleBindingSetsIterator(this, buffer, (StatementPattern) filterChild, bindings), this);
                } else if (filterChild instanceof Join) {
                    Join filterChildJoin = (Join) filterChild;
                    TupleExpr fcj_left = filterChildJoin.getLeftArg();
                    TupleExpr fcj_right = filterChildJoin.getRightArg();
                    //TODO: Should be a better way, maybe reorder the filter?
                    //very particular case filter(join(stmtPat, stmtPat))
                    if (fcj_left instanceof StatementPattern && fcj_right instanceof StatementPattern) {
                        return new FilterIterator(filter, new MultipleBindingSetsIterator(this, new Join(buffer, fcj_left), (StatementPattern) fcj_right, bindings), this);
                    }
                }
                //TODO: add a configuration flag for ParallelJoinIterator
                return new JoinIterator(this, join, bindings);
            } else {
                //TODO: add a configuration flag for ParallelJoinIterator
                return new JoinIterator(this, join, bindings);
            }
        } else {
            return super.evaluate(join, bindings);
        }
    }

    @Override
    public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(StatementPattern sp, BindingSet bindings) throws QueryEvaluationException {
        //TODO: Wonder if creating a Collection here hurts performance
        Set<BindingSet> bs = Collections.singleton(bindings);
        return this.evaluate(sp, bs);
    }

    public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(final StatementPattern sp, Collection<BindingSet> bindings)
            throws QueryEvaluationException {

        final Var subjVar = sp.getSubjectVar();
        final Var predVar = sp.getPredicateVar();
        final Var objVar = sp.getObjectVar();
        final Var cntxtVar = sp.getContextVar();

        List<Map.Entry<Statement, BindingSet>> stmts = new ArrayList<Map.Entry<Statement, BindingSet>>();

        Iteration<? extends Map.Entry<Statement, BindingSet>, QueryEvaluationException> iter;
        if (sp instanceof FixedStatementPattern) {
            Collection<Map.Entry<Statement, BindingSet>> coll = Lists.newArrayList();
            for (BindingSet binding : bindings) {
                Value subjValue = getVarValue(subjVar, binding);
                Value predValue = getVarValue(predVar, binding);
                Value objValue = getVarValue(objVar, binding);
                Resource contxtValue = (Resource) getVarValue(cntxtVar, binding);
                for (Statement st : ((FixedStatementPattern) sp).statements) {
                    if (!((subjValue != null && !subjValue.equals(st.getSubject())) ||
                            (predValue != null && !predValue.equals(st.getPredicate())) ||
                            (objValue != null && !objValue.equals(st.getObject())))) {
                        coll.add(new RdfCloudTripleStoreUtils.CustomEntry<Statement, BindingSet>(st, binding));
                    }
                }
            }
            iter = new IteratorIteration(coll.iterator());
        } else if (sp instanceof TransitivePropertySP &&
                ((subjVar != null && subjVar.getValue() != null) ||
                        (objVar != null && objVar.getValue() != null)) &&
                sp.getPredicateVar() != null) {
            //if this is a transitive prop ref, we need to make sure that either the subj or obj is not null
            //TODO: Cannot handle a open ended transitive property where subj and obj are null
            //TODO: Should one day handle filling in the subj or obj with bindings and working this
            //TODO: a lot of assumptions, and might be a large set returned causing an OME
            Set<Statement> sts = null;
            try {
                sts = inferenceEngine.findTransitiveProperty((Resource) getVarValue(subjVar),
                        (IRI) getVarValue(predVar), getVarValue(objVar), (Resource) getVarValue(cntxtVar));
            } catch (InferenceEngineException e) {
                throw new QueryEvaluationException(e);
            }
            Collection<Map.Entry<Statement, BindingSet>> coll = new ArrayList();
            for (BindingSet binding : bindings) {
                for (Statement st : sts) {
                    coll.add(new RdfCloudTripleStoreUtils.CustomEntry<Statement, BindingSet>(st, binding));
                }
            }
            iter = new IteratorIteration(coll.iterator());
        } else {
            for (BindingSet binding : bindings) {
                Value subjValue = getVarValue(subjVar, binding);
                Value predValue = getVarValue(predVar, binding);
                Value objValue = getVarValue(objVar, binding);
                Resource contxtValue = (Resource) getVarValue(cntxtVar, binding);
                if ((subjValue != null && !(subjValue instanceof Resource)) ||
                        (predValue != null && !(predValue instanceof IRI))) {
                    continue;
                }
                stmts.add(new RdfCloudTripleStoreUtils.CustomEntry<Statement, BindingSet>(
                        new NullableStatementImpl((Resource) subjValue, (IRI) predValue, objValue, contxtValue), binding));
            }
            if (stmts.size() == 0) {
                return new EmptyIteration();
            }

            iter = ((RdfCloudTripleStoreConnection.StoreTripleSource) tripleSource).getStatements(stmts);
        }
        return new ConvertingIteration<Map.Entry<Statement, BindingSet>, BindingSet, QueryEvaluationException>(iter) {

            @Override
            protected BindingSet convert(Map.Entry<Statement, BindingSet> stbs) throws QueryEvaluationException {
                Statement st = stbs.getKey();
                BindingSet bs = stbs.getValue();
                QueryBindingSet result = new QueryBindingSet(bs);
                //only add values to result BindingSet if Var is not constant and BindingSet doesn't already
                //contain a Value for that Var name
                if (subjVar != null && !subjVar.isConstant() && !result.hasBinding(subjVar.getName())) {
                    result.addBinding(subjVar.getName(), st.getSubject());
                }
                if (predVar != null && !predVar.isConstant() && !result.hasBinding(predVar.getName())) {
                    result.addBinding(predVar.getName(), st.getPredicate());
                }
                if (objVar != null && !objVar.isConstant() && !result.hasBinding(objVar.getName())) {
                    result.addBinding(objVar.getName(), st.getObject());
                }
                if (cntxtVar != null && !cntxtVar.isConstant() && !result.hasBinding(cntxtVar.getName()) && st.getContext() != null) {
                    result.addBinding(cntxtVar.getName(), st.getContext());
                }
                return result;
            }
        };
    }

    private BindingSet SanitizeBindingSetIfNecessary(BindingSet bindings)
    {
        BindingSet sanitizedBindingSet = bindings;
        Set<String> bindingNames = bindings.getBindingNames();

        List<String> validBindingsNames = bindingNames.stream()
                .filter(bindingName -> bindings.getBinding(bindingName) != null)
                .collect(Collectors.toList());

        if (bindingNames.size() > validBindingsNames.size())
        {
            MapBindingSet newBindings = new MapBindingSet();
            validBindingsNames.forEach(validBindingName -> newBindings.addBinding(bindings.getBinding(validBindingName)));

            sanitizedBindingSet = newBindings;
        }

        return sanitizedBindingSet;
    }

    @Override
    public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(TupleExpr expr, BindingSet bindings) throws QueryEvaluationException {
        if (expr instanceof QueryRoot) {
            if (displayQueryPlan) {
//                System.out.println("Tables: ");
//                System.out.println("--SPO: \t" + RdfCloudTripleStoreConstants.TBL_SPO);
//                System.out.println("--PO: \t" + RdfCloudTripleStoreConstants.TBL_PO);
//                System.out.println("--OSP: \t" + RdfCloudTripleStoreConstants.TBL_OSP);
                logger.info("=================== Rya Query ===================");
                for (String str : expr.toString().split("\\r?\\n")) {
                    logger.info(str);
                }
                logger.info("================= End Rya Query =================");
            }
        }

        bindings = SanitizeBindingSetIfNecessary(bindings);
        return super.evaluate(expr, bindings);
    }

    public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(Slice slice, BindingSet bindings)
            throws QueryEvaluationException {
        CloseableIteration<BindingSet, QueryEvaluationException> result = evaluate(slice.getArg(), bindings);
        if (slice.hasOffset()) {
            result = new OffsetIteration<BindingSet, QueryEvaluationException>(result, slice.getOffset());
        }
        if (slice.hasLimit()) {
            result = new LimitIteration<BindingSet, QueryEvaluationException>(result, slice.getLimit());
        }
        return result;
    }

    protected Value getVarValue(Var var) {
        if (var == null)
            return null;
        else
            return var.getValue();
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
