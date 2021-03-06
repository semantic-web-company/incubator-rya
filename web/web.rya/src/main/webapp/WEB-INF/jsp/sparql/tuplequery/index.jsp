<%@page import="org.eclipse.rdf4j.model.BNode"%>
<%@page import="org.eclipse.rdf4j.model.URI"%>
<%@taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@page contentType="application/xhtml+xml; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<fieldset>
    <legend>Results</legend>
    <table border="1">
    <tr>
        <c:forEach var="bindingName" items="${bindingNames}">
            <th>${bindingName}</th>
        </c:forEach>
    </tr>                
    <c:forEach var="bindingSet" items="${bindings}" varStatus="index">
        <tr>
            <c:forEach var="bindingName" items="${bindingNames}">
                <td>${fn:escapeXml(bindingSet.getValue(bindingName).stringValue())}</td>                
            </c:forEach>
        </tr>
    </c:forEach>
    </table>
</fieldset>
