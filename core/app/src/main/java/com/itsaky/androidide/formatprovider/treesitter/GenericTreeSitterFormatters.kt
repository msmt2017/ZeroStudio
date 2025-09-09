package com.itsaky.androidide.formatprovider.treesitter

// C-like languages formatter
class CLikeTreeSitterFormatter(languageType: String) : 
    TreeSitterFormatter(languageType, TreeSitterQuery.C_LIKE_LANG_QUERY)

// Python formatter
class PythonTreeSitterFormatter : 
    TreeSitterFormatter("python", TreeSitterQuery.PYTHON_QUERY)

// Generic XML formatter (for non-Android XML)
class XmlTreeSitterFormatter : 
    TreeSitterFormatter("xml", TreeSitterQuery.XML_QUERY)

// Shell formatter
class ShellTreeSitterFormatter : 
    TreeSitterFormatter("bash", TreeSitterQuery.SHELL_QUERY)

// Ruby formatter
class RubyTreeSitterFormatter :
    TreeSitterFormatter("ruby", TreeSitterQuery.RUBY_QUERY)

// Lua formatter
class LuaTreeSitterFormatter :
    TreeSitterFormatter("lua", TreeSitterQuery.LUA_QUERY)
    
// Go formatter
class GoTreeSitterFormatter :
    TreeSitterFormatter("go", TreeSitterQuery.GO_QUERY)