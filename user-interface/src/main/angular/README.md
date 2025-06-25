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
   component. You can also use  
   `ng generate directive|pipe|service|class|guard|interface|enum|module`.
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

## Internationlization (i18n) and Localization (l10n)

To facilitate translation of the user interface text into non-English languages, various
steps need to be taken to ensure that text that requires translation can be automatically
extracted, and automatically substituted into the user interface when required:

There are tools that can extract all the text marked for internationalization and compile
them into a resource file, which is essentially a list of the strings the user sees. These
can then be translated (often not by the author of the software, and sometimes by people
who have never used the software) into another language. For this reason code (as opposed
to promp text) should not be included in the resource strings, as translators might think
they have to translate that code somehow, which creates bugs.

### HTML files

In `.html` files, everything that contains user-facing text that doesn't come from the data should be marked with a `i18n` property.

- If the content of the tag is what will need translating, it's just i18n:\
  `<span i18n>Transcript</span>`
- In these cases, wherever possible, the content of the tag should contain no HTML or
  JavaScript code, so e.g.\
  `<div i18n>Please click <a src="...">here</a></div>`\
  should instead be\
  `<div><span i18n="Please click (here)">Please click</span>`\
  ` <a src="..." i18n="(Please click) here">here</a></div>`
- Where the `i18n` attribute has a value like this, it's text that will be available to
  the person who localizes into another language, so that when they're asked to translate
  e.g. "Please click" they know that it will be followed by "here", which may make an
  important difference in how they translate the text. 
- When it's an HTML *attribute* that will need localizing, the `i18n` attribute name is
  suffixed with the attribute name, e.g.\
  `<label i18n-title title="Tick to display this layer">{{layer.id}}</label>`
- To facilitate clarity and checking, try to keep the i18n attribute near what it's
  referring to, e.g. not:\
  `<summary i18n title="Display this layer" (click)="expand();" i18n-title>Expand</summary>`\
  But rather:\
  `<summary i18n-title title="Display this layer" (click)="expand();"  i18n>Expand</summary>`

### TypeScript code

Sometimes code in the `.ts` files generates user-facing language too. A formal mechanisms
for internationalizing these cases has not yet been decided. To make the retro-fitting the
final solution easier, tag every case where a change will need to be make with the comment
`// TODO i18n in the code`

e.g.
```
this.praatProgress = {
  message: `Opened: ${this.praatUtteranceName}`, // TODO i18n
  value: 100, maximum: 100, code: code }
```
