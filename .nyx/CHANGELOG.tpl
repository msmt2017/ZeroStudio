# AndroidIDE Changelog

{{#releases}}
## [{{name}}](https://github.com/msmt2017/ZeroStudio/releases/tag/{{name}}) ({{date}})

{{#sections}}
### {{name}}

{{#commits}}
* [{{#short5}}{{SHA}}{{/short5}}](https://github.com/msmt2017/ZeroStudio/commit/{{SHA}}) {{message.shortMessage}} ({{authorAction.identity.name}})

{{/commits}}
{{^commits}}
No changes.
{{/commits}}
{{/sections}}
{{^sections}}
No changes.
{{/sections}}
{{/releases}}
{{^releases}}
No releases.
{{/releases}}
