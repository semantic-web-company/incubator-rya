package org.apache.cloud.rdf.sparql.templating;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterFactory;
import org.springframework.web.servlet.View;

/**
 *
 * @author turnguard
 */
public class GraphQueryMAVWriterFactory implements MAVWriterFactory, RDFWriterFactory {

    @Override
    public RDFFormat getRDFFormat() {        
        return RDFFormat.RDFA;
    }

    public RDFWriter getWriter(View view, Map<String,Object> model, HttpServletRequest req, HttpServletResponse resp){
        return new GraphQueryMAVWriter(view, model, req, resp);
    }
    
    @Override
    public RDFWriter getWriter(OutputStream out) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RDFWriter getWriter(Writer writer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public RDFWriter getWriter(OutputStream out, String string) throws URISyntaxException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public RDFWriter getWriter(Writer writer, String string) throws URISyntaxException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
