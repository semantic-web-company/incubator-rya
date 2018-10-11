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

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.rya.indexing.accumulo.ConfigUtils;
import org.apache.rya.indexing.mongodb.MongoIndexingConfiguration;
import org.apache.rya.indexing.mongodb.MongoIndexingConfiguration.MongoDBIndexingConfigBuilder;
import org.apache.rya.rdftriplestore.RdfCloudTripleStore;
import org.apache.rya.rdftriplestore.inference.InferenceEngineException;
import org.apache.rya.sail.config.RyaSailFactory;
import org.apache.rya.test.mongo.EmbeddedMongoFactory;
import org.apache.zookeeper.ClientCnxn;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.UpdateExecutionException;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.Sail;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;



public class InferenceExamples {
	   private static final Logger log = Logger.getLogger(InferenceExamples.class);

	    private static final boolean IS_DETAILED_LOGGING_ENABLED = false;

	    //
	    // Connection configuration parameters
	    //

	    private static final boolean PRINT_QUERIES = true;
	    private static final String MONGO_DB = "rya";
	    private static final String MONGO_COLL_PREFIX = "rya_";
	    private static final boolean USE_EMBEDDED_MONGO = true;
	    private static final String MONGO_INSTANCE_URL = "localhost";
	    private static final String MONGO_INSTANCE_PORT = "27017";
	    private static final String MongoUserName="usern";
	    private static final String MongoUserPswd="passwd";

	    public static void setupLogging() {
	        final Logger rootLogger = LogManager.getRootLogger();
	        final ConsoleAppender ca = (ConsoleAppender) rootLogger.getAppender("stdout");
	        ca.setLayout(new PatternLayout("%d{MMM dd yyyy HH:mm:ss} %5p [%t] (%F:%L) - %m%n"));
	        rootLogger.setLevel(Level.INFO);
	        // Filter out noisy messages from the following classes.
	        Logger.getLogger(ClientCnxn.class).setLevel(Level.OFF);
	        Logger.getLogger(EmbeddedMongoFactory.class).setLevel(Level.OFF);
	    }

	    public static void main(final String[] args) throws Exception {
	        if (IS_DETAILED_LOGGING_ENABLED) {
	            setupLogging();
	        }
	        final Configuration conf = getConf();
	        conf.setBoolean(ConfigUtils.DISPLAY_QUERY_PLAN, PRINT_QUERIES);

	        SailRepository repository = null;
	        SailRepositoryConnection conn = null;
	        try {
	            log.info("Connecting to Indexing Sail Repository.");
	            final Sail sail = RyaSailFactory.getInstance(conf);
	            repository = new SailRepository(sail);
	            conn = repository.getConnection();


	            final long start = System.currentTimeMillis();

	                testInfer(conn, sail);
	                testPropertyChainInference(conn, sail);
	                testPropertyChainInferenceAltRepresentation(conn, sail);
	                testSomeValuesFromInference(conn, sail);
	                testAllValuesFromInference(conn, sail);
	                testIntersectionOfInference(conn, sail);
	                testOneOfInference(conn, sail);

	            log.info("TIME: " + (System.currentTimeMillis() - start) / 1000.);
	        } finally {
	            log.info("Shutting down");
	            closeQuietly(conn);
	            closeQuietly(repository);
	        }
	    }

	    private static void closeQuietly(final SailRepository repository) {
	        if (repository != null) {
	            try {
	                repository.shutDown();
	            } catch (final RepositoryException e) {
	                // quietly absorb this exception
	            }
	        }
	    }

	    private static void closeQuietly(final SailRepositoryConnection conn) {
	        if (conn != null) {
	            try {
	                conn.close();
	            } catch (final RepositoryException e) {
	                // quietly absorb this exception
	            }
	        }
	    }

	    private static Configuration getConf() throws IOException {

	       // MongoDBIndexingConfigBuilder builder = MongoIndexingConfiguration.builder()
	       //     .setUseMockMongo(USE_MOCK).setUseInference(USE_INFER).setAuths("U");
	        MongoDBIndexingConfigBuilder builder = MongoIndexingConfiguration.builder()
		            .setUseMockMongo(USE_EMBEDDED_MONGO).setUseInference(true).setAuths("U");

	        if (USE_EMBEDDED_MONGO) {
	            final MongoClient c = EmbeddedMongoFactory.newFactory().newMongoClient();
	            final ServerAddress address = c.getAddress();
	            final String url = address.getHost();
	            final String port = Integer.toString(address.getPort());
	            c.close();
	            builder.setMongoHost(url).setMongoPort(port);
	        } else {
	            // User name and password must be filled in:
	            builder = builder.setMongoUser(MongoUserName)
	                             .setMongoPassword(MongoUserPswd)
	                             .setMongoHost(MONGO_INSTANCE_URL)
	                             .setMongoPort(MONGO_INSTANCE_PORT);
	        }

	        return builder.setMongoDBName(MONGO_DB)
	               .setMongoCollectionPrefix(MONGO_COLL_PREFIX)
	               .setUseMongoFreetextIndex(true)
	               .setMongoFreeTextPredicates(RDFS.LABEL.stringValue()).build();

	    }


	    public static void testPropertyChainInferenceAltRepresentation(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
	    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {

	        // Add data
	        String query = "INSERT DATA\n"//
	                + "{ GRAPH <http://updated/test> {\n"//
	                + "  <urn:jenGreatGranMother> <urn:Motherof> <urn:jenGranMother> . "
	                + "  <urn:jenGranMother> <urn:isChildOf> <urn:jenGreatGranMother> . "
	                + "  <urn:jenGranMother> <urn:Motherof> <urn:jenMother> . "
	                + "  <urn:jenMother> <urn:isChildOf> <urn:jenGranMother> . "
	                + " <urn:jenMother> <urn:Motherof> <urn:jen> . "
	                + "  <urn:jen> <urn:isChildOf> <urn:jenMother> . "
	                + " <urn:jen> <urn:Motherof> <urn:jenDaughter> .  }}";

	        log.info("Performing Query");

	        Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
	        update.execute();

	        query = "select ?p { GRAPH <http://updated/test> {?s <urn:Motherof>/<urn:Motherof> ?p}}";
	        CountingResultHandler resultHandler = new CountingResultHandler();
	        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());


	        // try adding a property chain and querying for it
	        query = "INSERT DATA\n"//
	                + "{ GRAPH <http://updated/test> {\n"//
	                + "  <urn:greatMother> owl:propertyChainAxiom <urn:12342>  . " +
	                " <urn:12342> <http://www.w3.org/1999/02/22-rdf-syntax-ns#first> _:node1atjakcvbx15023 . " +
	                " _:node1atjakcvbx15023 <http://www.w3.org/2002/07/owl#inverseOf> <urn:isChildOf> . " +
	                " <urn:12342> <http://www.w3.org/1999/02/22-rdf-syntax-ns#rest> _:node1atjakcvbx15123 . " +
	                   " _:node1atjakcvbx15123 <http://www.w3.org/1999/02/22-rdf-syntax-ns#rest> <http://www.w3.org/1999/02/22-rdf-syntax-ns#nil> . " +
	                " _:node1atjakcvbx15123 <http://www.w3.org/1999/02/22-rdf-syntax-ns#first> <urn:MotherOf> .  }}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
	        update.execute();
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();

	        resultHandler.resetCount();
	        query = "select ?x { GRAPH <http://updated/test> {<urn:jenGreatGranMother> <urn:greatMother> ?x}}";
	        resultHandler = new CountingResultHandler();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());

	    }

	    public static void testPropertyChainInference(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
	    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {

	        // Add data
	        String query = "INSERT DATA\n"//
	                + "{ GRAPH <http://updated/test> {\n"//
	                + "  <urn:paulGreatGrandfather> <urn:father> <urn:paulGrandfather> . "
	                + "  <urn:paulGrandfather> <urn:father> <urn:paulFather> . " +
	                " <urn:paulFather> <urn:father> <urn:paul> . " +
	                " <urn:paul> <urn:father> <urn:paulSon> .  }}";

	        log.info("Performing Query");

	        Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
	        update.execute();

	        query = "select ?p { GRAPH <http://updated/test> {<urn:paulGreatGrandfather> <urn:father>/<urn:father> ?p}}";
	        CountingResultHandler resultHandler = new CountingResultHandler();
	        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());


	        // try adding a property chain and querying for it
	        query = "INSERT DATA\n"//
	                + "{ GRAPH <http://updated/test> {\n"//
	                + "  <urn:greatGrandfather> owl:propertyChainAxiom <urn:1234>  . " +
	                " <urn:1234> <http://www.w3.org/2000/10/swap/list#length> 3 . " +
	                " <urn:1234> <http://www.w3.org/2000/10/swap/list#index> (0 <urn:father>) . " +
	                " <urn:1234> <http://www.w3.org/2000/10/swap/list#index> (1 <urn:father>) . " +
	                " <urn:1234> <http://www.w3.org/2000/10/swap/list#index> (2 <urn:father>) .  }}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
	        update.execute();
	        query = "INSERT DATA\n"//
	                + "{ GRAPH <http://updated/test> {\n"//
	                + "  <urn:grandfather> owl:propertyChainAxiom <urn:12344>  . " +
	                " <urn:12344> <http://www.w3.org/2000/10/swap/list#length> 2 . " +
	                " <urn:12344> <http://www.w3.org/2000/10/swap/list#index> (0 <urn:father>) . " +
	                " <urn:12344> <http://www.w3.org/2000/10/swap/list#index> (1 <urn:father>) .  }}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
	        update.execute();
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();

	        resultHandler.resetCount();
	        query = "select ?p { GRAPH <http://updated/test> {<urn:paulGreatGrandfather> <urn:greatGrandfather> ?p}}";
	        resultHandler = new CountingResultHandler();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());

	        resultHandler.resetCount();
	        query = "select ?s ?p { GRAPH <http://updated/test> {?s <urn:grandfather> ?p}}";
	        resultHandler = new CountingResultHandler();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());

	    }

	    public static void testIntersectionOfInference(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException, UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {
	        log.info("Adding Data");
	        final String instances = "INSERT DATA\n"
	                + "{ GRAPH <http://updated/test> {\n"
	                + "  <urn:Susan> a <urn:Mother> . \n"
	                + "  <urn:Mary> a <urn:Woman> . \n"
	                + "  <urn:Mary> a <urn:Parent> . \n"
	                + "}}";
	        Update update = conn.prepareUpdate(QueryLanguage.SPARQL, instances);
	        update.execute();
	        final String inferQuery = "select distinct ?x { GRAPH <http://updated/test> { ?x a <urn:Mother> }}";
	        final String explicitQuery = "select distinct ?x { GRAPH <http://updated/test> {\n"
	                + "  { ?x a <urn:Mother> }\n"
	                + "  UNION {\n"
	                + "    ?x a <urn:Woman> .\n"
	                + "    ?x a <urn:Parent> .\n"
	                + "  }\n"
	                + "}}";
	        log.info("Running Explicit Query");
	        CountingResultHandler resultHandler = new CountingResultHandler();
	        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, explicitQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 2);
	        log.info("Running Inference-dependant Query");
	        resultHandler.resetCount();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 1);
	        log.info("Adding owl:intersectionOf Schema");
	        // ONTOLOGY - :Mother intersectionOf[:Woman, :Parent]
	        final String ontology = "INSERT DATA\n"
	                + "{ GRAPH <http://updated/test> {\n"
	                + "  <urn:Mother> owl:intersectionOf _:bnode1 . \n"
	                + "  _:bnode1 rdf:first <urn:Woman> . \n"
	                + "  _:bnode1 rdf:rest _:bnode2 . \n"
	                + "  _:bnode2 rdf:first <urn:Parent> . \n"
	                + "  _:bnode2 rdf:rest rdf:nil . \n"
	               + "}}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, ontology);
	        update.execute();
	        log.info("Refreshing InferenceEngine");
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();
	        log.info("Re-running Inference-dependant Query");
	        resultHandler.resetCount();
	        resultHandler = new CountingResultHandler();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 2);
	    }

	    public static void testSomeValuesFromInference(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
	    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {
	        final String lubm = "http://swat.cse.lehigh.edu/onto/univ-bench.owl#";
	        log.info("Adding Data");
	        String insert = "PREFIX lubm: <" + lubm + ">\n"
	                + "INSERT DATA { GRAPH <http://updated/test> {\n"
	                + "  <urn:Department0> a lubm:Department; lubm:subOrganizationOf <urn:University0> .\n"
	                + "  <urn:ResearchGroup0> a lubm:ResearchGroup; lubm:subOrganizationOf <urn:Department0> .\n"
	                + "  <urn:Alice> lubm:headOf <urn:Department0> .\n"
	                + "  <urn:Bob> lubm:headOf <urn:ResearchGroup0> .\n"
	                + "  <urn:Carol> lubm:worksFor <urn:Department0> .\n"
	                + "}}";
	        Update update = conn.prepareUpdate(QueryLanguage.SPARQL, insert);
	        update.execute();
	        final String inferQuery = "select distinct ?x { GRAPH <http://updated/test> { ?x a <" + lubm + "Chair> }}";
	        final String explicitQuery = "prefix lubm: <" + lubm + ">\n"
	                + "select distinct ?x { GRAPH <http://updated/test> {\n"
	                + "  { ?x a lubm:Chair }\n"
	                + "  UNION\n"
	                + "  { ?x lubm:headOf [ a lubm:Department ] }\n"
	                + "}}";
	        log.info("Running Explicit Query");
	        final CountingResultHandler resultHandler = new CountingResultHandler();
	        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, explicitQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 1);
	        log.info("Running Inference-dependent Query");
	        resultHandler.resetCount();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 0);
	        log.info("Adding owl:someValuesFrom Schema");
	        insert = "PREFIX rdfs: <" + RDFS.NAMESPACE + ">\n"
	                + "PREFIX owl: <" + OWL.NAMESPACE + ">\n"
	                + "PREFIX lubm: <" + lubm + ">\n"
	                + "INSERT DATA\n"
	                + "{ GRAPH <http://updated/test> {\n"
	                + "  lubm:Chair owl:equivalentClass [ owl:onProperty lubm:headOf ; owl:someValuesFrom lubm:Department ] ."
	                + "}}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, insert);
	        update.execute();
	        log.info("Refreshing InferenceEngine");
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();
	        log.info("Re-running Inference-dependent Query");
	        resultHandler.resetCount();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 1);
	    }

	    public static void testAllValuesFromInference(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
	    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {
	        log.info("Adding Data");
	        String insert = "INSERT DATA\n"
	                + "{ GRAPH <http://updated/test> {\n"
	                + "  <urn:Alice> a <urn:Person> .\n"
	                + "  <urn:Alice> <urn:hasParent> <urn:Bob> .\n"
	                + "  <urn:Carol> <urn:hasParent> <urn:Dan> .\n"
	                + "}}";
	        Update update = conn.prepareUpdate(QueryLanguage.SPARQL, insert);
	        update.execute();
	        final String inferQuery = "select distinct ?x { GRAPH <http://updated/test> { ?x a <urn:Person> }}";
	        final String explicitQuery = "select distinct ?x { GRAPH <http://updated/test> {\n"
	                + "  { ?x a <urn:Person> }\n"
	                + "  UNION {\n"
	                + "    ?y a <urn:Person> .\n"
	                + "    ?y <urn:hasParent> ?x .\n"
	                + "  }\n"
	                + "}}";
	        log.info("Running Explicit Query");
	        final CountingResultHandler resultHandler = new CountingResultHandler();
	        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, explicitQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 2);
	        log.info("Running Inference-dependent Query");
	        resultHandler.resetCount();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 1);
	        log.info("Adding owl:allValuesFrom Schema");
	        insert = "PREFIX rdfs: <" + RDFS.NAMESPACE + ">\n"
	                + "PREFIX owl: <" + OWL.NAMESPACE + ">\n"
	                + "INSERT DATA\n"
	                + "{ GRAPH <http://updated/test> {\n"
	                + "  <urn:Person> rdfs:subClassOf [ owl:onProperty <urn:hasParent> ; owl:allValuesFrom <urn:Person> ] ."
	                + "}}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, insert);
	        update.execute();
	        log.info("Refreshing InferenceEngine");
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();
	        log.info("Re-running Inference-dependent Query");
	        resultHandler.resetCount();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 2);
	    }

	    public static void testOneOfInference(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException, UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {
	        log.info("Adding Data");
	        final String instances = "INSERT DATA"
	                + "{ GRAPH <http://updated/test> {\n"
	                + "  <urn:FlopCard1> a <urn:Card> . \n"
	                + "    <urn:FlopCard1> <urn:HasRank> <urn:Ace> . \n"
	                + "    <urn:FlopCard1> <urn:HasSuit> <urn:Diamonds> . \n"
	                + "  <urn:FlopCard2> a <urn:Card> . \n"
	                + "    <urn:FlopCard2> <urn:HasRank> <urn:Ace> . \n"
	                + "    <urn:FlopCard2> <urn:HasSuit> <urn:Hearts> . \n"
	                + "  <urn:FlopCard3> a <urn:Card> . \n"
	                + "    <urn:FlopCard3> <urn:HasRank> <urn:King> . \n"
	                + "    <urn:FlopCard3> <urn:HasSuit> <urn:Spades> . \n"
	                + "  <urn:TurnCard> a <urn:Card> . \n"
	                + "    <urn:TurnCard> <urn:HasRank> <urn:10> . \n"
	                + "    <urn:TurnCard> <urn:HasSuit> <urn:Clubs> . \n"
	                + "  <urn:RiverCard> a <urn:Card> . \n"
	                + "    <urn:RiverCard> <urn:HasRank> <urn:Queen> . \n"
	                + "    <urn:RiverCard> <urn:HasSuit> <urn:Hearts> . \n"
	                + "}}";
	        Update update = conn.prepareUpdate(QueryLanguage.SPARQL, instances);
	        update.execute();
	        final String explicitQuery = "select distinct ?card { GRAPH <http://updated/test> {\n"
	                + "  ?card a <urn:Card> . \n"
	                + "  VALUES ?suit { <urn:Clubs> <urn:Diamonds> <urn:Hearts> <urn:Spades> } . \n"
	                + "  ?card <urn:HasSuit> ?suit . \n"
	                + "}}";
	        log.info("Running Explicit Query");
	        CountingResultHandler resultHandler = new CountingResultHandler();
	        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, explicitQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 5);
	        log.info("Adding owl:oneOf Schema");
	        // ONTOLOGY - :Suits oneOf (:Clubs, :Diamonds, :Hearts, :Spades)
	        // ONTOLOGY - :Ranks oneOf (:Ace, :1, :2, :3, :4, :5, :6, :7, :8, :9, :10, :Jack, :Queen, :King)
	        final String ontology = "INSERT DATA { GRAPH <http://updated/test> {\n"
	                + "  <urn:Suits> owl:oneOf _:bnodeS1 . \n"
	                + "  _:bnodeS1 rdf:first <urn:Clubs> . \n"
	                + "  _:bnodeS1 rdf:rest _:bnodeS2 . \n"
	                + "  _:bnodeS2 rdf:first <urn:Diamonds> . \n"
	                + "  _:bnodeS2 rdf:rest _:bnodeS3 . \n"
	                + "  _:bnodeS3 rdf:first <urn:Hearts> . \n"
	                + "  _:bnodeS3 rdf:rest _:bnodeS4 . \n"
	                + "  _:bnodeS4 rdf:first <urn:Spades> . \n"
	                + "  _:bnodeS4 rdf:rest rdf:nil . \n"
	                + "  <urn:Ranks> owl:oneOf _:bnodeR1 . \n"
	                + "  _:bnodeR1 rdf:first <urn:Ace> . \n"
	                + "  _:bnodeR1 rdf:rest _:bnodeR2 . \n"
	                + "  _:bnodeR2 rdf:first <urn:2> . \n"
	                + "  _:bnodeR2 rdf:rest _:bnodeR3 . \n"
	                + "  _:bnodeR3 rdf:first <urn:3> . \n"
	                + "  _:bnodeR3 rdf:rest _:bnodeR4 . \n"
	                + "  _:bnodeR4 rdf:first <urn:4> . \n"
	                + "  _:bnodeR4 rdf:rest _:bnodeR5 . \n"
	                + "  _:bnodeR5 rdf:first <urn:5> . \n"
	                + "  _:bnodeR5 rdf:rest _:bnodeR6 . \n"
	                + "  _:bnodeR6 rdf:first <urn:6> . \n"
	                + "  _:bnodeR6 rdf:rest _:bnodeR7 . \n"
	                + "  _:bnodeR7 rdf:first <urn:7> . \n"
	                + "  _:bnodeR7 rdf:rest _:bnodeR8 . \n"
	                + "  _:bnodeR8 rdf:first <urn:8> . \n"
	                + "  _:bnodeR8 rdf:rest _:bnodeR9 . \n"
	                + "  _:bnodeR9 rdf:first <urn:9> . \n"
	                + "  _:bnodeR9 rdf:rest _:bnodeR10 . \n"
	                + "  _:bnodeR10 rdf:first <urn:10> . \n"
	                + "  _:bnodeR10 rdf:rest _:bnodeR11 . \n"
	                + "  _:bnodeR11 rdf:first <urn:Jack> . \n"
	                + "  _:bnodeR11 rdf:rest _:bnodeR12 . \n"
	                + "  _:bnodeR12 rdf:first <urn:Queen> . \n"
	                + "  _:bnodeR12 rdf:rest _:bnodeR13 . \n"
	                + "  _:bnodeR13 rdf:first <urn:King> . \n"
	                + "  _:bnodeR13 rdf:rest rdf:nil . \n"
	                + "  <urn:Card> owl:intersectionOf (\n"
	                + "    [ owl:onProperty <urn:HasRank> ; owl:someValuesFrom <urn:Ranks> ]\n"
	                + "    [ owl:onProperty <urn:HasSuit> ; owl:someValuesFrom <urn:Suits> ]\n"
	                + "  ) . \n"
	                + "  <urn:HasRank> owl:range <urn:Ranks> . \n"
	                + "  <urn:HasSuit> owl:range <urn:Suits> . \n"
	                + "}}";
	        update = conn.prepareUpdate(QueryLanguage.SPARQL, ontology);
	        update.execute();
	        log.info("Running Inference-dependent Query without refreshing InferenceEngine");
	        resultHandler.resetCount();
	        final String inferQuery = "select distinct ?card { GRAPH <http://updated/test> {\n"
	                + "  ?card a <urn:Card> . \n"
	                + "  ?suit a <urn:Suits> . \n"
	                + "  ?card <urn:HasSuit> ?suit . \n"
	                + "}}";
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 0);
	        log.info("Refreshing InferenceEngine");
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();
	        log.info("Re-running Inference-dependent Query");
	        resultHandler.resetCount();
	        resultHandler = new CountingResultHandler();
	        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, inferQuery);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());
	        Validate.isTrue(resultHandler.getCount() == 5);
	    }

	    public static void testInfer(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
	    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {

	        // Add data
	        String query = "INSERT DATA\n"//
	                + "{ \n"//
	                + " <http://acme.com/people/Mike> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <urn:type1>.  "
	                + " <urn:type1> <http://www.w3.org/2000/01/rdf-schema#subClassOf> <urn:superclass>.  }";

	        log.info("Performing Query");

	        final Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
	        update.execute();

	        // refresh the graph for inferencing (otherwise there is a five minute wait)
	        ((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();

	        query = "select ?s { ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <urn:superclass> . }";
	        final CountingResultHandler resultHandler = new CountingResultHandler();
	        final TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
	        tupleQuery.evaluate(resultHandler);
	        log.info("Result count : " + resultHandler.getCount());

	        Validate.isTrue(resultHandler.getCount() == 1);

	        resultHandler.resetCount();
	    }

	    private static class CountingResultHandler implements TupleQueryResultHandler {
	        private int count = 0;

	        public int getCount() {
	            return count;
	        }

	        public void resetCount() {
	            count = 0;
	        }

	        @Override
	        public void startQueryResult(final List<String> arg0) throws TupleQueryResultHandlerException {
	        }

	        @Override
	        public void handleSolution(final BindingSet arg0) throws TupleQueryResultHandlerException {
	            count++;
	            System.out.println(arg0);
	        }

	        @Override
	        public void endQueryResult() throws TupleQueryResultHandlerException {
	        }

	        @Override
	        public void handleBoolean(final boolean arg0) throws QueryResultHandlerException {
	        }

	        @Override
	        public void handleLinks(final List<String> arg0) throws QueryResultHandlerException {
	        }
	    }
}
