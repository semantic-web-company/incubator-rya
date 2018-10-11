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
package org.apache.rya.mongodb;

import org.apache.hadoop.conf.Configuration;
import org.apache.rya.api.RdfCloudTripleStoreConfigurationBuilder;

/**
 * This builder class will set all of the core Mongo-backed Rya configuration
 * parameters. Any builder extending this class will have setter methods for all
 * of the necessary parameters to connect to a Mongo backed Rya instance.
 *
 * @param <B>
 *            - builder returned by setter methods extending this class
 * @param <C>
 *            - configuration object constructed by the builder extending this
 *            class
 */
public abstract class AbstractMongoDBRdfConfigurationBuilder<B extends AbstractMongoDBRdfConfigurationBuilder<B, C>, C extends MongoDBRdfConfiguration>
        extends RdfCloudTripleStoreConfigurationBuilder<B, C> {

    private String user;
    private String pass;
    private boolean useMock = false;
    private String host = "localhost";
    private String port = DEFAULT_MONGO_PORT;
    public static final String DEFAULT_MONGO_PORT = "27017";
    private String mongoDBName = "rya";
    private boolean usePipeline = false;

    public static final String MONGO_USER = "mongo.user";
    public static final String MONGO_PASSWORD = "mongo.password";
    public static final String MONGO_DB_NAME = "mongo.db.name";
    public static final String MONGO_HOST = "mongo.host";
    public static final String MONGO_PORT = "mongo.port";
    public static final String MONGO_AUTHS = "mongo.auths";
    public static final String MONGO_VISIBILITIES = "mongo.visibilities";
    public static final String MONGO_RYA_PREFIX = "mongo.rya.prefix";
    public static final String USE_INFERENCE = "use.inference";
    public static final String USE_DISPLAY_QUERY_PLAN = "use.display.plan";
    public static final String USE_MOCK_MONGO = "use.mock";

    /**
     * Sets Mongo user.
     *
     * @param user - user name used to connect to Mongo
     * @return specified builder for chaining method invocations
     */
    public B setMongoUser(final String user) {
        this.user = user;
        return confBuilder();
    }

    /**
     * Sets password for Mongo user specified by
     * {@link AbstractMongoDBRdfConfigurationBuilder#setMongoUser(String)}.
     *
     * @param password - password used to connect to Mongo
     * @return specified builder for chaining method invocations
     */
    public B setMongoPassword(final String password) {
        this.pass = password;
        return confBuilder();
    }

    /**
     * Sets Mongo port. This parameter must be set to connect to an instance of
     * MongoDB and will default to "27017" if no value is specified.
     *
     * @param port - port used to connect Mongo
     * @return specified builder for chaining method invocations
     */
    public B setMongoPort(final String port) {
        this.port = port;
        return confBuilder();
    }

    /**
     * Sets Mongo host. This parameter must be set to connect to an instance of
     * MongoDB and will default to "localhost" if no value is specified.
     *
     * @param host - host used to connect to Mongo
     * @return specified builder for chaining method invocations
     */
    public B setMongoHost(final String host) {
        this.host = host;
        return confBuilder();
    }

    /**
     * Sets MongoDB name. This parameter must be set to connect to an instance
     * of MongoDB and will default to "rya_triples" is no value is specified.
     *
     * @param name - name of MongoDB to connect to
     * @return specified builder for chaining method invocations
     */
    public B setMongoDBName(final String name) {
        this.mongoDBName = name;
        return confBuilder();
    }

    /**
     * Set whether to use instance of embedded Mongo as backend for Rya
     * instance.
     *
     * @param useMock - indicates whether to use embedded Mongo as Rya backing
     * @return specified builder for chaining method invocations
     */
    public B setUseMockMongo(final boolean useMock) {
        this.useMock = useMock;
        return confBuilder();
    }

    /**
     * Enable or disable an optimization that executes queries, to the extent
     * possible, using the MongoDB aggregation pipeline. Defaults to false.
     * If true, replaces a query tree or subtree with a single node representing
     * a series of pipeline steps. Transformation may not be supported for all
     * query algebra expressions; these expressions are left unchanged and the
     * optimization is attempted on their child subtrees.
     * @param usePipeline whether to use aggregation pipeline optimization.
     */
    public B setUseAggregationPipeline(final boolean usePipeline) {
        this.usePipeline = usePipeline;
        return confBuilder();
    }

    /**
     * @return extension of {@link MongoDBRdfConfiguration} with specified parameters set
     */
    @Override
    public C build() {
        return getConf(super.build());
    }

    /**
     * Assigns builder values to appropriate parameters within the {@link Configuration} object.
     *
     * @param conf - Configuration object
     * @return - Configuration object with parameters set
     */
    private C getConf(final C conf) {

        conf.setUseMock(useMock);
        conf.set("sc.useMongo", "true");

        if (user != null) {
            conf.setMongoUser(user);
        }
        if (pass != null) {
            conf.setMongoPassword(pass);
        }
        conf.setRyaInstanceName(mongoDBName);
        conf.setMongoHostname(host);
        conf.setMongoPort(port);
        conf.setUseAggregationPipeline(usePipeline);

        return conf;
    }

}
