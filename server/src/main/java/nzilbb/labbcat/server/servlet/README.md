# API endpoint implementations 

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
`HttpServletResponse` only. Up until now, these have been directly references from
precompiled code in this directory.

JSP files are compiled on demand by the servlet container in which they're running, so
derive and depend on whatever package names are implemented by that container. This means
that the same JSP file will work on Tomcat 9 and Tomcat 10 seamlessly, as long as they use
objects and methods that are common to both Java EE and Jakarta EE.

This means it's possible to implement the code that directly implement HTTP request
handling in JSP pages in a Tomcat-version-agnostic manner. 

In order to maintain the advantages of precompiled Java code (compile-time syntax and type
checking, and automated unit testing), code handling API requests is split between classes
in `src/main/java/nzilbb/labbcat/server/api/` (which implement 'business logic')
and JSP pages in `src/main/jsp/` (which implement request parsing and response encoding).

## Archive compilation

This project currently produces a 'jar' archive which is included in the 'labbcat.war'
archived built elsewhere. In order to ensure that these jsp files implement the correct
endpoints in the resulting web application:

- The .jsp files in `src/main/jsp/` are packaged in a subdirectory called
  `META-INF/resources/jsp/`
- `web-fragment.xml`, which maps request endpoints to jsp files, is packaged in `META-INF/`
