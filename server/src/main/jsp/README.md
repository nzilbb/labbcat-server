# JSP resources

## Java EE vs. Jakarta EE

Releases of Apache Tomcat up to and including version 9 implemented the standard Java
Servlet API which was known as 'Java EE'.

Releases of Apache Tomcat from version 10 onward instead use 'Jakarta EE', which
implements the a very similar object model, but with different package names. Essentially,
instead of classes like `javax.servlet.http.HttpServlet`, Tomcat 10+ uses classes like
`jakarta.servlet.http.HttpServlet`.

This means that precompiled servlet classes must extend a class with a different package
name, which would be fine if we could dictate and control what version of Tomcat LaBB-CAT
users have, and closely manage migration from Tomcat 9 to Tomcat 10. 

Unfortunately, this is not the case. Many different users have legacy servers running
Tomcat 8 or 9, and given that the package names are different, it's not possible to
distribute a single *labbcat.war* file that will work both with Tomcat 9 **and** with
Tomcat 10 (without significant initial and ongoing coding and configuration overhead). 

## JSP solution

The main dependencies in this project are extensions of the `HttpServlet` class, which
respond to HTTP requests, and so depend mainly on `HttpServletRequest` and
`HttpServletResponse` only. Up until now, these have been directly referenced from
precompiled code in `src/main/java/nzilbb/labbcat/server/servlet/`

JSP files are compiled on demand by the servlet container in which they're running, so
derive and depend on whatever package names are implemented by that container. This means
that the same JSP file will work on Tomcat 9 and Tomcat 10 seamlessly, as long as they use
objects and methods that are common to both Java EE and Jakarta EE.

This means it's possible to implement the code that directly implements HTTP request
handling in JSP pages in a Tomcat-version-agnostic manner. 

In order to maintain the advantages of precompiled Java code (compile-time syntax and type
checking, and automated unit testing), code handling API requests is split between classes
in `src/main/java/nzilbb/labbcat/server/api/` (which implement 'business logic') and
JSP pages here (which implement request parsing and response encoding).

### REST-style requests

JSP files handle GET and POST request only by default. The LaBB-CAT API defines PUT and
DELETE requests as well, which would not be handled.

The workaround for this issue is currently to mark all jsp pages as `isErrorPage="true"`;
in both Tomcat 9 and Tomcat 10, JSP pages that can serve errors are allowed to handle any
HTTP method, including PUT and DELETE.

(This does not work in another Java servler container, JBoss/WildFly, which only allows
GET and POST methods.)

### File Uploads

Jakarta EE is pretty much a drop-in replacement for Java EE, with the same object model,
and class/method names. One exception is the handling of the multi-part HTTP requests that
are used to upload files via HTML forms. Tomcat 9 and Tomcat 10 work slightly differently,
using the `org.apache.commons.fileupload` and `org.apache.commons.fileupload2` packages
respectively, and thus require different JSP code for uploads.

The difference is reflected in the two files:
 - file-upload-tomcat9.jsp
 - file-upload-tomcat10.jsp

Endpoints that require file uploads conditionally include the correct JSP at runtime,
depending on which version of Tomcat they're running.

## Archive compilation

This project currently produces a 'jar' archive which is included in the 'labbcat.war'
archived built elsewhere. In order to ensure that these jsp files implement the correct
endpoints in the resulting web application:

- The .jsp files here are packaged in a subdirectory called `META-INF/resources/jsp/`
- `web-fragment.xml`, which maps request endpoints to jsp files, is packaged in `META-INF/`
