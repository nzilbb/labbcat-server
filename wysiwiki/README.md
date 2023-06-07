# ckeditor

This is a custom build of [CKEditor 5](https://github.com/ckeditor) for wysiwiki documentation
pages.

## Development Build

```
./node_modules/.bin/webpack --mode development
```

## Release Build

```
mvn package
```

This leaves output files in `target/wysiwiki/`
