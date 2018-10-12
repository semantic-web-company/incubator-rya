
<!--

[comment]: # Licensed to the Apache Software Foundation (ASF) under one
[comment]: # or more contributor license agreements.  See the NOTICE file
[comment]: # distributed with this work for additional information
[comment]: # regarding copyright ownership.  The ASF licenses this file
[comment]: # to you under the Apache License, Version 2.0 (the
[comment]: # "License"); you may not use this file except in compliance
[comment]: # with the License.  You may obtain a copy of the License at
[comment]: # 
[comment]: #   http://www.apache.org/licenses/LICENSE-2.0
[comment]: # 
[comment]: # Unless required by applicable law or agreed to in writing,
[comment]: # software distributed under the License is distributed on an
[comment]: # "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
[comment]: # KIND, either express or implied.  See the License for the
[comment]: # specific language governing permissions and limitations
[comment]: # under the License.

-->
# Load Data

There are a few mechanisms to load data.

## Web REST endpoint

The War sets up a Web REST endpoint at `http://server/web.rya/loadrdf` that allows POST data to get loaded into the Rdf Store. This short tutorial will use Java code to post data.

First, you will need data to load and will need to figure out what format that data is in.

For this sample, we will use the following N-Triples:

```
<http://mynamespace/ProductType1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://mynamespace/ProductType> .
<http://mynamespace/ProductType1> <http://www.w3.org/2000/01/rdf-schema#label> "Thing" .
<http://mynamespace/ProductType1> <http://purl.org/dc/elements/1.1/publisher> <http://mynamespace/Publisher1> .
```

Save this file somewhere `$RDF_DATA`

Second, use the following Java code to load data to the REST endpoint:

``` JAVA
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class LoadDataServletRun {

    public static void main(String[] args) {
        try {
            final InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("$RDF_DATA");
            URL url = new URL("http://server/web.rya/loadrdf" +
                    "?format=N-Triples" +
                    "");
            URLConnection urlConnection = url.openConnection();
            urlConnection.setRequestProperty("Content-Type", "text/plain");
            urlConnection.setDoOutput(true);

            final OutputStream os = urlConnection.getOutputStream();

            int read;
            while((read = resourceAsStream.read()) >= 0) {
                os.write(read);
            }
            resourceAsStream.close();
            os.flush();

            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                System.out.println(line);
            }
            rd.close();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

Compile and run this code above, changing the references for $RDF_DATA and the url that your Rdf War is running at.

The default "format" is RDF/XML, but these formats are supported : RDFXML, NTRIPLES, TURTLE, N3, TRIX, TRIG.

## Bulk Loading data

Bulk loading data is done through Map Reduce jobs.

### Bulk Load RDF data

This Map Reduce job will read files into memory and parse them into statements. The statements are saved into the triplestore. 
Here are the steps to prepare and run the job:

  * Load the RDF data to HDFS. It can be single of multiple volumes and can have directories in them.
  * Also load the `mapreduce/target/rya.mapreduce-<version>-shaded.jar` executable jar file to HDFS.
  * Run the following sample command:

```
hadoop hdfs://volume/rya.mapreduce-<version>-shaded.jar org.apache.rya.accumulo.mr.tools.RdfFileInputTool -Dac.zk=localhost:2181 -Dac.instance=accumulo -Dac.username=root -Dac.pwd=secret -Drdf.tablePrefix=rya_ -Drdf.format=N-Triples hdfs://volume/dir1,hdfs://volume/dir2,hdfs://volume/file1.nt
```

Options:

- **rdf.tablePrefix** - The tables (spo, po, osp) are prefixed with this qualifier.
    The tables become: (rdf.tablePrefix)spo,(rdf.tablePrefix)po,(rdf.tablePrefix)osp
- **ac.*** - Accumulo connection parameters
- **rdf.format** - See RDFFormat from RDF4J, samples include (Trig, N-Triples, RDF/XML)
- **sc.use_freetext, sc.use_geo, sc.use_temporal, sc.use_entity** - If any of these are set to true, statements will also be
    added to the enabled secondary indices.
- **sc.freetext.predicates, sc.geo.predicates, sc.temporal.predicates** - If the associated indexer is enabled, these options specify
    which statements should be sent to that indexer (based on the predicate). If not given, all indexers will attempt to index
    all statements.

The positional argument is a comma separated list of directories/files to load.
They need to be loaded into HDFS before running. If loading a directory,
all files should have the same RDF format.

Once the data is loaded, it is actually a good practice to compact your tables.
You can do this by opening the accumulo shell and running the compact
command on the generated tables. Remember the generated tables will be
prefixed by the `rdf.tablePrefix` property you assigned above.
The default tablePrefix is `rya_`.

Here is a sample Accumulo Shell command:

```
compact -p triplestore_(.*)
```

### Generate Prospects table

For the best query performance, it is recommended to run the job that
creates the Prospects table. This job will read through your data and
gather statistics on the distribution of the dataset. This table is then
queried before query execution to reorder queries based on the data
distribution. See the [Prospects Table](eval.md) section on how to do this.

## Direct RDF4J API

Here is some sample code to load data directly through the RDF4J API. (Loading N-Triples data)
You will need at least `accumulo.rya-<version>`, `rya.api`, `rya.sail.impl` on the classpath and transitive dependencies. I find that Maven is the easiest way to get a project dependency tree set up.

``` JAVA
final RdfCloudTripleStore store = new RdfCloudTripleStore();
AccumuloRdfConfiguration conf = new AccumuloRdfConfiguration();
AccumuloRyaDAO dao = new AccumuloRdfDAO();
Connector connector = new ZooKeeperInstance("instance", "zoo1,zoo2,zoo3").getConnector("user", "password");
dao.setConnector(connector);
conf.setTablePrefix("rya_");
dao.setConf(conf);
store.setRdfDao(dao);

Repository myRepository = new RyaSailRepository(store);
myRepository.initialize();
RepositoryConnection conn = myRepository.getConnection();

//load data from file
final File file = new File("ntriples.ntrips");
conn.add(new FileInputStream(file), file.getName(),
        RDFFormat.NTRIPLES, new Resource[]{});

conn.commit();

conn.close();
myRepository.shutDown();
```
