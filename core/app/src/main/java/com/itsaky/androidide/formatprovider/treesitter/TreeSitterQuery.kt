package com.itsaky.androidide.formatprovider.treesitter

/**
 * A central repository for Tree-sitter queries used for code formatting.
 * Each query is designed to identify specific syntax nodes for indentation adjustments.
 */
object TreeSitterQuery {

    // 专门为 Groovy DSL 设计的查询
    const val GROOVY_GRADLE_QUERY = """
    [
      (closure_block)
      (argument_list)
    ] @indent

    "{" @indent.start
    "}" @indent.end
    
    (comment) @ignore
    """
    // A generic query for most C-like languages (Java, C++, C#, JS, TS, etc.)
    const val C_LIKE_LANG_QUERY = """
    [
      (block)
      (object)
      (declaration_list)
      (switch_body)
      (class_body)
      (struct_specifier)
      (union_specifier)
      (enum_specifier)
      (compound_statement)
      (array_initializer)
      (arguments)
    ] @indent

    [ "{" "[" "(" ] @indent.start
    [ "}" "]" ")" ] @indent.end

    (preproc_if) @indent
    (preproc_elif) @indent.branch
    (preproc_else) @indent.branch
    (preproc_endif) @indent.end
    
    (case_statement) @indent.branch
    
    (comment) @ignore
    """
    
    const val PYTHON_QUERY = """
    [
      (block)
      (class_definition body: (_))
      (function_definition body: (_))
      (parameters)
      (list)
      (tuple)
      (dictionary)
    ] @indent
    (comment) @ignore
    """
    
    const val XML_QUERY = """
    (element 
      open_tag: (_) @indent.start
      close_tag: (_) @indent.end) @indent
    (comment) @ignore
    (doctype) @ignore
    """

    const val SHELL_QUERY = """
    [
      (do_group)
      (if_statement)
      (case_item)
    ] @indent

    "then" @indent.start
    "fi" @indent.end
    "do" @indent.start
    "done" @indent.end
    "esac" @indent.end
    
    (comment) @ignore
    """
    
    const val RUBY_QUERY = """
    [
      (block)
      (class)
      (module)
      (method)
      (if)
      (while)
      (for)
      (case)
    ] @indent

    (end) @indent.end
    
    (comment) @ignore
    """
    
    const val LUA_QUERY = """
    [
      (block)
      (function_body)
      (if_statement)
      (while_statement)
      (for_statement)
      (repeat_statement)
    ] @indent

    "then" @indent.start
    "do" @indent.start
    
    [ "end" "until" ] @indent.end
    
    (comment) @ignore
    """

    const val GO_QUERY = """
    [
      (block)
      (struct_type)
      (interface_type)
      (parameter_list)
      (expression_switch_statement)
    ] @indent
    
    [ "{" "(" "[" ] @indent.start
    [ "}" ")" "]" ] @indent.end

    (comment) @ignore
    """
}