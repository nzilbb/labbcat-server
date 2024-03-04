cd user-interface/src/main/angular
ng extract-i18n labbcat-view --output-path projects/labbcat-view/src/locale
ng extract-i18n labbcat-edit --output-path projects/labbcat-edit/src/locale
ng extract-i18n labbcat-admin --output-path projects/labbcat-admin/src/locale
head -n 4 projects/labbcat-view/src/locale/messages.xlf > src/locale/messages.xlf
xmllint --xpath "//*['trans-unit'=local-name()]" projects/labbcat-view/src/locale/messages.xlf >> src/locale/messages.xlf
xmllint --xpath "//*['trans-unit'=local-name()]" projects/labbcat-edit/src/locale/messages.xlf >> src/locale/messages.xlf
xmllint --xpath "//*['trans-unit'=local-name()]" projects/labbcat-admin/src/locale/messages.xlf >> src/locale/messages.xlf
tail -n 3 projects/labbcat-view/src/locale/messages.xlf >> src/locale/messages.xlf
