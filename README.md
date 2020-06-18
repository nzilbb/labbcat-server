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

### Deployment into LaBB-CAT

To deploy a production version of the user interface into a local installation of
LaBB-CAT:

1. Check that the *local-labbcat-path* setting in *config.xml* is correct.
2. `ant user-interface`

