# nzilbb.labbcat.server

[LaBB-CAT](https://labbcat.canterbury.ac.nz) is a linguistic annotation store server,
implemented by the [NZILBB](http://www.nzilbb.canterbury.ac.nz).

The 'classic' [legacy code](https://sourceforge.net/projects/labbcat/) is in the process
of being deprecated and replaced by the code in this repository.

There currently three components here:
- servlet implementations, which are implemented in Java and packaged into
  *nzilbb.labbcat.server.jar* which is included as a library in the 'classic'
  distribution. (eventually, the war build will be included here, at which point the entire
  legacy repository will be deprecated)
- parts of the user-interface, which are implemented as components of an Angular app,
  which currently replace certain pages in the 'classic' codebase. (eventually, the entire
  user interface will be replaced by the Angular app)
- In-situ wiki-style corpus documentation system with WYSIWYG editor, including embedding
  of utterances from search results, called 'wysiwiki'

## Servlets

### Build targets

- `mvn package -pl :nzilbb.labbcat.server` - tests and builds *target/nzilbb.labbcat.server-n.n.n.jar*
- `mvn package -pl :nzilbb.labbcat.server -Dmaven.test.skip` - builds *target/nzilbb.labbcat.server-n.n.n.jar* without running tests
- `mvn site -pl :nzilbb.labbcat.server` - build javadoc/API documentation in *docs*.

#### Test prerequisites

If automated tests are run, they require the following in order to complete successfully:

- A test LaBB-CAT instance available at http://localhost:8080/labbcat/
- Password protection
- An 'admin' user with the credentials specified in\
  `server/src/test/java/nzilbb/labbcat/server/api/TestStore.java`
- A 'view'-only user with the credentials specified in\
  `server/src/test/java/nzilbb/labbcat/server/api/admin/roles/TestPermissions.java`
- Some transcripts uploaded (e.g. the Demo corpus)
- One transcript that matches the pattern `AP511.+\.eaf`
- No empty corpora
- A layer called `phonemes` with annotations
- The first participant on the 'participants' page to have a value for `participant_gender`
  and for `participant_notes`
- The first participant must have been force-aligned.
- The plain text formatter installed
- The Praat TextGrid formatter installed

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
2. *xmllint*, *head*, and *tail* for collating i18n resources

### Debugging / Development

1. Ensure you have a local development instance of LaBB-CAT installed
(e.g. at http://localhost:8080/labbcat)
2. Ensure your local LaBB-CAT instance has user authentication disabled and the CORS filter
enabled in  ${config.local-labbcat-path}/WEB-INF/web.xml
3. `cd user-interface/src/main/angular`
4. `ng serve`

The default app is *labbcat-admin*. To serve the view/edit apps,
use `ng serve labbcat-view` or `ng serve labbcat-edit` (respectively)

### Internationalization/Localization

The user interface components contain `i18n` attributes for resources that require
translation to other languages. If changes are made, resource files can be generated for
translation by executing the following shell script:

```
./i18n.sh
```

This generates localization resources for all three app projects, and merges them
into a single file:  
*user-interface/src/main/angular/src/locale/messages.xlf*

To localize to a new language/variety:
1. Copy *user-interface/src/main/angular/src/locale/messages.xlf* with a new name formatted
   *messages.{language-code}-{country-code}.xlf* -
   e.g. *messages.es-AR.xlf* for Argentine Spanish.
2. Edit the new file with an XLIFF editor.
3. Add the new locale to the "locales" setting in *user-interface/angular.json*

For more information about how to internationalize the code so that such translation can
occur, see `user-interface/src/main/angular/README.md`

### Deployment into LaBB-CAT

To deploy a production version of the user interface into a local installation of
LaBB-CAT, execute the following shell script:

```
./deploy-user-interface.sh
```

The shell script assumed the location of the local LaBB-CAT instance is:\
`/var/lib/tomcat9/webapps/labbcat`

You can specify a different location as a command-line parameter, e.g.:

```
./deploy-user-interface.sh /opt/tomcat/webapps/labbcat
```

#### Troubleshooting

If you get an error something like:

> [INFO] An unhandled exception occurred: The service was stopped: spawn /home/robert/nzilbb/labbcat-server/user-interface/target/angular/node_modules/@esbuild/linux-x64/bin/esbuild EACCES
> ...
> [ERROR] Failed to execute goal com.github.eirslett:frontend-maven-plugin:1.12.1:npm (user-interface-view) on project nzilbb.labbcat.user-interface: Failed to run task: 'npm run build-labbcat-view' failed. org.apache.commons.exec.ExecuteException: Process exited with an error: 127 (Exit value: 127) -> [Help 1]

...it means that the *esbuild* command is not marked as executable on your system. To fix that:

```
chmod a+x user-interface/target/angular/node_modules/@esbuild/linux-x64/bin/esbuild
```

## Wysiwiki

Requires Node and npm.

To build:

```
mvn package -pl :nzilbb.labbcat.wysiwiki
```

To deploy into LaBB-CAT:

```
cp wysiwiki/target/wysiwiki/* /var/lib/tomcat9/webapps/labbcat/wysiwiki/
```

## Docker image

To build the docker image:

1. copy the latest version of *labbcat.war* to be copied into the *bin* directory.
2. `docker build -t nzilbb/labbcat .`

To release a new version of the docker image:

1. Execute:  
   docker push nzilbb/labbcat
2. Tag the build with the version number:  
   docker tag nzilbb/labbcat nzilbb/labbcat:`unzip -qc bin/labbcat.war version.txt`
3. Execute:  
   docker push nzilbb/labbcat:`unzip -qc bin/labbcat.war version.txt`

The image does not include a MySQL server, which can be supplied from the MySQL docker
image:

```
docker run --name=labbcat-db \
 -e MYSQL_DATABASE=labbcat -e MYSQL_USER=labbcat \
 -e MYSQL_PASSWORD=secret \
 -d mysql/mysql-server:5.6 \
 --skip-log-bin --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci
docker run -v /path/to/external/directory:/labbcat/files --name=labbcat --link labbcat-db -d -p 8888:8080 nzilbb/labbcat
```
