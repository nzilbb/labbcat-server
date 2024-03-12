# user-interface

The LaBB-CAT browser-based user interface is implemented with three angular apps:

- *labbcat-view* - for 'read-only' functions common to all users,
- *labbcat-edit* - for 'read-write' functions available to users with 'edit' access, and
- *labbcat-admin* - for administration functions available to users with 'admin' access.

Each of these share some core functionality, implemented in the *labbcat-common* library.

Using three apps is more complicated to work with than one, but improves performance for
most users, who are doing read-only operations most of the time - i.e. the functions
provided by *labbcat-view*.

Before splitted into different apps for different levels of access, the single
user-interface app bundled to a size of 3MB, which over slow networks, caused a noticeable
delay loading the search results page the first time. After the split, *labbcat-view*
bundles to 1.1MB, which consequently loads 60% faster.

## Development server

Run `ng serve` for a development server (*labbcat-admin* is the default app). Then
navigate to `http://localhost:4200/`. The app will automatically reload if you change any
of the source files. 

## Handy Angular commands:

- `ng serve labbcat-view` to run *labbcat-view* instead of *labbcat-admin*
- `ng serve labbcat-edit` to run *labbcat-edit* instead of *labbcat-admin*
- `ng build labbcat-common` to rebuild the common library so that apps incorporate library
   changes (which they don't automatically)
- `ng generate component component-name --project project-name` to generate a new 
   component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`. 

## Production build

See README.md in user-interface directory.
