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

## npm, node, and angular versions

It's recommended to use a user-based installation of `npm`, etc. to avoid
permission/version issues - i.e.

```
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
nvm install --lts --latest-npm
npm install -g @angular/cli
```

## Development server

Run `ng serve` for a development server (*labbcat-admin* is the default app). Then
navigate to `http://localhost:4200/`. The app will automatically reload if you change any
of the source files. 

## Handy Angular commands:

- `ng serve labbcat-view` to run *labbcat-view*
- `ng serve labbcat-edit` to run *labbcat-edit*
- `ng serve labbcat-admin` to run *labbcat-admin*
- `ng build labbcat-common` to rebuild the common library so that apps incorporate library
   changes (which they don't automatically)
- `ng generate component component-name --project project-name` to generate a new 
   component. You can also use `ng generate
- directive|pipe|service|class|guard|interface|enum|module`.
- For production builds (outputs go to ../../../dist):
  - `npm run build-labbcat-common`
  - `npm run build-labbcat-view`
  - `npm run build-labbcat-edit`
  - `npm run build-labbcat-admin`

## Production build

See README.md in user-interface directory.

## Upgrading node/npm/packages

```
nvm install --lts --reinstall-packages-from=default --latest-npm
node -v
npm -v
```

... then update `nodeVersion` and `npmVersion` in *../../../pom.xml*
