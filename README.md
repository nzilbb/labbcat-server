# nzilbb.labbcat.server

Implementation for the nzilbb.labbcat.server.jar library, which implements selected parts
of the [LaBB-CAT](https://labbcat.canterbury.ac.nz) linguistic annotation store server,
implemented by the [NZILBB](http://www.nzilbb.canterbury.ac.nz).

*NB* The LaBB-CAT codebase is currently in the process of rewriting and migration. This
repository contains the new code. The legacy code, much of which is still in use, is here:

https://sourceforge.net/projects/labbcat/ 

## Dependencies

- [nzilbb.ag.jar](https://github.com/nzilbb/ag)

## Build targets

- `ant` - builds bin/nzilbb.labbcat.server.jar
- `ant test` - also runs unit tests
- `ant javadoc` - also produces JavaDoc API documentation.

## Documentation

More documentation is available [here](https://nzilbb.github.io/labbcat-server/)