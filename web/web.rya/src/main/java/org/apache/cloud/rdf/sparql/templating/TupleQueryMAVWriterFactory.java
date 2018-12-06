package org.apache.cloud.rdf.sparql.templating;

import java.io.OutputStream;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriter;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory;
import org.springframework.web.servlet.View;

/**
 *
 * @author turnguard
 */
public class TupleQueryMAVWriterFactory implements MAVWriterFactory, TupleQueryResultWriterFactory {

    @Override
    public TupleQueryResultFormat getTupleQueryResultFormat() {
        return TupleQueryMAVWriter.XHTML;
    }

    @Override
    public TupleQueryResultWriter getWriter(OutputStream out) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public TupleQueryResultWriter getWriter(View view, Map<String,Object> model, HttpServletRequest req, HttpServletResponse resp){
        return new TupleQueryMAVWriter(view, model, req, resp);
    }
            
}
