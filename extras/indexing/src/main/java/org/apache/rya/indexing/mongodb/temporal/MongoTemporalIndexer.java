package org.apache.rya.indexing.mongodb.temporal;
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

import static org.apache.rya.indexing.mongodb.temporal.TemporalMongoDBStorageStrategy.INSTANT;
import static org.apache.rya.indexing.mongodb.temporal.TemporalMongoDBStorageStrategy.INTERVAL_END;
import static org.apache.rya.indexing.mongodb.temporal.TemporalMongoDBStorageStrategy.INTERVAL_START;

import org.apache.log4j.Logger;
import org.apache.rya.indexing.StatementConstraints;
import org.apache.rya.indexing.TemporalIndexer;
import org.apache.rya.indexing.TemporalInstant;
import org.apache.rya.indexing.TemporalInterval;
import org.apache.rya.indexing.accumulo.ConfigUtils;
import org.apache.rya.indexing.mongodb.AbstractMongoIndexer;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.QueryEvaluationException;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.DBCollection;
import com.mongodb.QueryBuilder;

/**
 * Indexes MongoDB based on time instants or intervals.
 */
public class MongoTemporalIndexer extends AbstractMongoIndexer<TemporalMongoDBStorageStrategy> implements TemporalIndexer {
    private static final String COLLECTION_SUFFIX = "temporal";
    private static final Logger LOG = Logger.getLogger(MongoTemporalIndexer.class);

    @Override
    public void init() {
        initCore();
        predicates = ConfigUtils.getTemporalPredicates(conf);
        if(predicates.size() == 0) {
            LOG.debug("No predicates specified for temporal indexing.  During insertion, all statements will be attempted to be indexed into the temporal indexer.");
        }
        storageStrategy = new TemporalMongoDBStorageStrategy();
        storageStrategy.createIndices(collection);
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantEqualsInstant(
            final TemporalInstant queryInstant, final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INSTANT)
            .is(queryInstant.getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantBeforeInstant(
            final TemporalInstant queryInstant, final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INSTANT)
            .lessThan(queryInstant.getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantAfterInstant(
            final TemporalInstant queryInstant, final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INSTANT)
            .greaterThan(queryInstant.getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantBeforeInterval(
            final TemporalInterval givenInterval, final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INSTANT)
            .lessThan(givenInterval.getHasBeginning().getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantAfterInterval(
            final TemporalInterval givenInterval, final StatementConstraints constraints) throws QueryEvaluationException {
        return queryInstantAfterInstant(givenInterval.getHasEnd(), constraints);
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantInsideInterval(
            final TemporalInterval givenInterval, final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INSTANT)
            .greaterThan(givenInterval.getHasBeginning().getAsDateTime().toDate())
            .lessThan(givenInterval.getHasEnd().getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantHasBeginningInterval(
            final TemporalInterval queryInterval, final StatementConstraints constraints) throws QueryEvaluationException {
        return queryInstantEqualsInstant(queryInterval.getHasBeginning(), constraints);
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryInstantHasEndInterval(
            final TemporalInterval queryInterval, final StatementConstraints constraints) throws QueryEvaluationException {
        return queryInstantEqualsInstant(queryInterval.getHasEnd(), constraints);
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryIntervalEquals(final TemporalInterval query,
            final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INTERVAL_START)
            .is(query.getHasBeginning().getAsDateTime().toDate())
            .and(INTERVAL_END)
            .is(query.getHasEnd().getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryIntervalBefore(final TemporalInterval query,
            final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INTERVAL_END)
            .lessThan(query.getHasBeginning().getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public CloseableIteration<Statement, QueryEvaluationException> queryIntervalAfter(final TemporalInterval query,
            final StatementConstraints constraints) throws QueryEvaluationException {
        final QueryBuilder qb = QueryBuilder.start(INTERVAL_START)
            .greaterThan(query.getHasEnd().getAsDateTime().toDate());
        return withConstraints(constraints, qb.get());
    }

    @Override
    public String getCollectionName() {
        return ConfigUtils.getTablePrefix(conf)  + COLLECTION_SUFFIX;
    }

    @VisibleForTesting
    public DBCollection getCollection() {
        return collection;
    }
}
