# nzilbb.labbcat.server

[LaBB-CAT](https://labbcat.canterbury.ac.nz) is a linguistic annotation store server,
implemented by the [NZILBB](http://www.nzilbb.canterbury.ac.nz).

The 'classic' [legacy code](https://sourceforge.net/projects/labbcat/) is in the process
of being deprecated and replaced by the code in this repository.

There currently two components here:
- servlet implementations, which are implemented in Java and packaged into
*nzilbb.labbcat.server.jar* which is included as a library in the 'classic'
distribution. (eventually, the war build will be included here, at which point the entire
legacy repository will be deprecated)
- parts of the user-interface, which are implemented as components of an Angular app,
which currently replace certain pages in the 'classic' codebase. (eventually, the entire
user interface will be replaced by the Angular app)

## Servlets

### Dependencies

- [nzilbb.ag.jar](https://github.com/nzilbb/ag)

### Build targets

- `ant` - builds bin/nzilbb.labbcat.server.jar
- `ant test` - also runs unit tests
- `ant javadoc` - also produces JavaDoc API documentation.

### Documentation

More documentation is available [here](https://nzilbb.github.io/labbcat-server/)

## User interface

The user interface is broken into three angular apps
- *labbcat-view* - for 'read-only' functions common to all users,
- *labbcat-edit* - for 'read-write' functions available to users with 'edit' access, and
- *labbcat-admin* - for administration functions available to users with 'admin' access.

### Dependencies

1. Node and npm
2. Angular CLI
   `npm install -g @angular/cli`

### Debugging / Development

1. Ensure you have a local development instance of LaBB-CAT installed
(e.g. at http://localhost:8080/labbcat)
2. Ensure your local LaBB-CAT instance has user authentication disabled and the CORS filter
enabled in  ${config.local-labbcat-path}/WEB-INF/web.xml
3. `cd user-interface`
4. `ng serve`

The default app is *labbcat-admin*. To serve the view/edit apps,
use `ng serve labbcat-view` or `ng serve labbcat-edit` (respectively)

### Internationalization/Localization

The user interface components are contain `i18n` attributes for resources that require
translation to other languages. If changes are made, resource files can be generated for
translation by executing:

```
ant i18n
```

To localize to a new language/variety:
1. Copy *user-interface/src/locale/messages.xlf* with a new name formatted
   *messages.{language-code}-{country-code}.xlf* -
   e.g. *messages.es-AR.xlf* for Argentine Spanish.
2. Edit the new file with an XLIFF editor.
3. Add the new locale to the "locales" setting in *user-interface/angular.json*


### Deployment into LaBB-CAT

To deploy a production version of the user interface into a local installation of
LaBB-CAT:

1. Check that the *local-labbcat-path* setting in *config.xml* is correct.
2. `ant user-interface`

## Docker image

To build the docker image:

1. copy the latest version of *labbcat.war* to be copied into the *bin* directory.
2. docker build -t nzilbb/labbcat .

To release a new version of the docker image:

1. Execute:
   docker push nzilbb/labbcat
2. Tag the build with the version number:
   docker tag nzilbb/labbcat nzilbb/labbcat:`unzip -qc bin/labbcat.war version.txt`
3. Execute:
   docker push nzilbb/labbcat:`unzip -qc bin/labbcat.war version.txt`

The image does not include a MySQL server, which can be supplied from the MySQL docker
image:

TODO add  option:
```
docker run --name=labbcat-db \
 -e MYSQL_DATABASE=labbcat -e MYSQL_USER=labbcat \
 -e MYSQL_PASSWORD=secret \
 -d mysql/mysql-server:5.6 \
 --skip-log-bin --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci
docker run --name=labbcat --link labbcat-db -d -p 8888:8080 nzilbb/labbcat
```
