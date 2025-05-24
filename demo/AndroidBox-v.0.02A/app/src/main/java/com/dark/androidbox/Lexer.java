package com.dark.androidbox;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.Modifier;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Lexer {

    public CompilationUnit unit;
    public JavaParser parser;
    public Lexer(StringBuilder code) {
        parser = new JavaParser();
        unit = parser.parse(new StringReader(code.toString())).getResult().orElse(null);
    }

    public List<ClassOrInterfaceDeclaration> getClasses() {
        return unit.findAll(ClassOrInterfaceDeclaration.class);
    }

    public List<MethodDeclaration> getMethods() {
        return unit.findAll(MethodDeclaration.class);
    }

    public List<FieldDeclaration> getFields() {
        return unit.findAll(FieldDeclaration.class);
    }

    public List<MethodCallExpr> getMethodCalls() {
        return unit.findAll(MethodCallExpr.class);
    }

    public void addClass(String className, String extendedClass, List<String> implementedInterfaces) {
        // Create a new class
        ClassOrInterfaceDeclaration newClass = unit.addClass(className);

        // Add the extended class (parent class) if provided
        if (extendedClass != null && !extendedClass.isEmpty()) {
            newClass.addExtendedType(extendedClass); // Add the "extends" clause
        }

        // Add implemented interfaces if provided
        if (implementedInterfaces != null && !implementedInterfaces.isEmpty()) {
            for (String interfaceName : implementedInterfaces) {
                newClass.addImplementedType(interfaceName); // Add the "implements" clause
            }
        }
    }



    // Add a field to a specific class
    public void addFieldToClass(String className, String fieldType, String fieldName) {
        Optional<ClassOrInterfaceDeclaration> classOpt = unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> c.getNameAsString().equals(className))
                .findFirst();

        classOpt.ifPresent(classDeclaration -> {
            FieldDeclaration newField = classDeclaration.addField(fieldType, fieldName, Modifier.Keyword.PRIVATE);
            classDeclaration.addMember(newField); // Add the field to the class
        });
    }

    // Add a method to a specific class
    public void addMethodToClass(String className, String methodName, String returnType) {
        Optional<ClassOrInterfaceDeclaration> classOpt = unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> c.getNameAsString().equals(className))
                .findFirst();

        classOpt.ifPresent(classDeclaration -> {
            MethodDeclaration newMethod = classDeclaration.addMethod(methodName, Modifier.Keyword.PUBLIC);
            newMethod.setType(returnType); // Set the return type
            newMethod.setBody(new BlockStmt()); // Add an empty body
            classDeclaration.addMember(newMethod); // Add the method to the class
        });
    }

    // Add local variables to a method body
    public void addLocalVariableToMethod(String methodName, String varType, String varName) {
        Optional<MethodDeclaration> methodOpt = unit.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .findFirst();

        methodOpt.ifPresent(method -> {
            VariableDeclarationExpr varDecl = new VariableDeclarationExpr(parser.parseParameter(varType).getResult().get().getType(), varName);
            ExpressionStmt varStmt = new ExpressionStmt(varDecl); // Create a statement from the variable declaration
            method.getBody().ifPresent(body -> body.addStatement(varStmt)); // Add the variable declaration to the method body
        });
    }

    // Add parameters to a method signature
    public void addParameterToMethod(String className, String methodName, String paramType, String paramName) {
        Optional<MethodDeclaration> methodOpt = unit.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .findFirst();

        methodOpt.ifPresent(method -> {
            Parameter parameter = new Parameter(parser.parseParameter(paramType).getResult().get().getType(), paramName);
            method.addParameter(parameter); // Add the parameter to the method
        });
    }
}
