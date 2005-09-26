<%@ page import="org.apache.axis2.Constants"%>
<%@ page import="org.apache.axis2.context.ConfigurationContext"%>
<%@ page import="org.apache.axis2.context.ServiceContext"%>
<%@ page import="org.apache.axis2.context.ServiceGroupContext"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Iterator"%>
<%--
  Created by IntelliJ IDEA.
  User: Indika Deepal
  Date: Sep 20, 2005
  Time: 9:16:56 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<jsp:include page="include/adminheader.jsp"></jsp:include>
<h1>Runing Context hierachy</h1>
<%
    ConfigurationContext configContext = (ConfigurationContext)request.getSession().getAttribute(
            Constants.CONFIG_CONTEXT);

    HashMap serviceGroupContextsMap = configContext.getServiceGroupContexts();
    Iterator serviceGroupContext = serviceGroupContextsMap.keySet().iterator();
    if(serviceGroupContextsMap.size() >0){
    %>
     <ul>
    <%
    while (serviceGroupContext.hasNext()) {
        String groupContextID = (String)serviceGroupContext.next();
        ServiceGroupContext groupContext = (ServiceGroupContext)serviceGroupContextsMap.get(groupContextID);
        %>
           <li><%=groupContextID%><font color="blue"><a href="viewServiceGroupContext.jsp?TYPE=VIEW&ID=<%=groupContextID%>">
                    View</a></font>  <font color="red"><a href="viewServiceGroupContext.jsp?TYPE=DELETE&ID=<%=groupContextID%>">
                    Remove</a> </font></li>
        <%
        Iterator serviceContextItr = groupContext.getServiceContexts();
            %><ul><%
        while (serviceContextItr.hasNext()) {
            ServiceContext serviceContext = (ServiceContext)serviceContextItr.next();
             String serviceConID = serviceContext.getServiceInstanceID();
        %>
            <li><%=serviceConID%><font color="blue"><a href="viewServiceContext.jsp?TYPE=VIEW&ID=<%=serviceConID%>&PID=<%=groupContextID%>">
                    View</a></font></li>
        <%
        }
                %></ul><hr><%
    }
    %>  </ul>
        <%
            }
%>
<jsp:include page="include/adminfooter.jsp"></jsp:include>