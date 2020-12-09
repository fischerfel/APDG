package com.aisec.sa.util;

import java.io.PrintStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PPABindingsUtil;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;

public class PPATypeVisitor extends ASTVisitor {

	private PrintStream printer;
	
	public PPATypeVisitor(PrintStream printer) {
		super();
		this.printer = printer;
	}
//	  NullLiteral, NumberLiteral, ParenthesizedExpression, PostfixExpression, PrefixExpression, StringLiteral, SuperFieldAccess, SuperMethodInvocation, ThisExpression, TypeLiteral, VariableDeclarationExpression
	
	@Override
	public void postVisit(ASTNode node) {
		super.postVisit(node);

		if (node instanceof Expression) {
			Expression exp = (Expression) node;

			IBinding binding = null;
			if (exp instanceof Name) {
				Name name = (Name) exp;
				binding = name.resolveBinding();
				System.out.println("Node: " + node.toString() + " is name");
			} else if (exp instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation) exp;
				binding = mi.resolveMethodBinding();
				System.out.println("Node: " + node.toString() + " is method");
			} else if (exp instanceof ClassInstanceCreation) {
				ClassInstanceCreation cic = (ClassInstanceCreation) exp;
				binding = cic.resolveConstructorBinding();
				System.out.println("Node: " + node.toString() + " is instance creation");
			} else if (exp instanceof Annotation) {
				Annotation a = (Annotation) exp;
				binding = a.resolveAnnotationBinding();
			} else if (exp instanceof Assignment) {
				Assignment a = (Assignment) exp;
				binding = a.resolveTypeBinding();
			} else if (exp instanceof CastExpression) {
				CastExpression ce = (CastExpression) exp;
				binding = ce.resolveTypeBinding();
			} else if (exp instanceof CharacterLiteral) {
				CharacterLiteral cl = (CharacterLiteral) exp;
				binding = cl.resolveTypeBinding();
			} else if (exp instanceof ConditionalExpression) {
				ConditionalExpression ce = (ConditionalExpression) exp;
				binding = ce.resolveTypeBinding();
			} else if (exp instanceof FieldAccess) {
				FieldAccess fa = (FieldAccess) exp;
				binding = fa.resolveFieldBinding();
			} else if (exp instanceof InfixExpression) {
				InfixExpression ie = (InfixExpression) exp;
				binding = ie.resolveTypeBinding();
			} else if (exp instanceof NullLiteral) {
				NullLiteral nl = (NullLiteral) exp;
				binding = nl.resolveTypeBinding();
			} else if (exp instanceof ParenthesizedExpression) {
				ParenthesizedExpression pe = (ParenthesizedExpression) exp;
				binding = pe.resolveTypeBinding();
			} else if (exp instanceof PostfixExpression) {
				PostfixExpression pe = (PostfixExpression) exp;
				binding = pe.resolveTypeBinding();
			} else if (exp instanceof PrefixExpression) {
				PrefixExpression pe = (PrefixExpression) exp;
				binding = pe.resolveTypeBinding();
			} else if (exp instanceof StringLiteral) {
				StringLiteral sl = (StringLiteral) exp; 
				binding = sl.resolveTypeBinding();
			} else if (exp instanceof SuperFieldAccess) {
				SuperFieldAccess sfa = (SuperFieldAccess) exp;
				binding = sfa.resolveFieldBinding();
			} else if (exp instanceof SuperMethodInvocation) {
				SuperMethodInvocation smi = (SuperMethodInvocation) exp;
				binding = smi.resolveMethodBinding();
			} else if (exp instanceof ThisExpression) {
				ThisExpression te = (ThisExpression) exp;
				binding = te.resolveTypeBinding();
			} else if (exp instanceof TypeLiteral) {
				TypeLiteral tl = (TypeLiteral) exp;
				binding = tl.resolveTypeBinding();
				System.out.println("Node: " + node.toString() + " is type literal");
			} else if (exp instanceof VariableDeclarationExpression) {
				VariableDeclarationExpression vde = (VariableDeclarationExpression) exp;
				binding = vde.resolveTypeBinding();
			} else {
				return;
			}
			printer.println("Node: " + node.toString());
			printer.println("  Parent: " + node.getParent().toString());
			ITypeBinding tBinding = exp.resolveTypeBinding();
			if (tBinding != null) {
				printer.println("  Type Binding: " + tBinding.getQualifiedName());
			} else {
				System.out.println(node.toString() + " has no type binding");
			}

			if (binding != null) {
				printer.println("  " + PPABindingsUtil.getBindingText(binding));
			}
			printer.flush();
		}
	}

}