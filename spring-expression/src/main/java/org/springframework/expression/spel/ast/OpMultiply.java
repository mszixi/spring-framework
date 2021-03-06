/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.expression.spel.ast;

import java.math.BigDecimal;

import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.util.NumberUtils;

/**
 * Implements the {@code multiply} operator.
 *
 * <p>Conversions and promotions are handled as defined in
 * <a href="http://java.sun.com/docs/books/jls/third_edition/html/conversions.html">Section
 * 5.6.2 of the Java Language Specification</a>, with the addiction of {@code BigDecimal} management:
 *
 * <p>If any of the operands is of a reference type, unboxing conversion (Section 5.1.8)
 * is performed. Then:<br>
 * If either operand is of type {@code BigDecimal}, the other is converted to {@code BigDecimal}.<br>
 * If either operand is of type double, the other is converted to double.<br>
 * Otherwise, if either operand is of type float, the other is converted to float.<br>
 * Otherwise, if either operand is of type long, the other is converted to long.<br>
 * Otherwise, both operands are converted to type int.
 *
 * @author Andy Clement
 * @author Sam Brannen
 * @author Giovanni Dall'Oglio Risso
 * @since 3.0
 */
public class OpMultiply extends Operator {


	public OpMultiply(int pos, SpelNodeImpl... operands) {
		super("*", pos, operands);
	}


	/**
	 * Implements the {@code multiply} operator directly here for certain types
	 * of supported operands and otherwise delegates to any registered overloader
	 * for types not supported here.
	 * <p>Supported operand types:
	 * <ul>
	 * <li>{@code BigDecimal}
	 * <li>doubles
	 * <li>longs
	 * <li>integers
	 * <li>String and int ('abc' * 2 == 'abcabc')
	 * </ul>
	 */
	@Override
	public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {

		Object leftOperand = getLeftOperand().getValueInternal(state).getValue();
		Object rightOperand = getRightOperand().getValueInternal(state).getValue();

		if (leftOperand instanceof Number && rightOperand instanceof Number) {
			Number leftNumber = (Number) leftOperand;
			Number rightNumber = (Number) rightOperand;
			if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
				BigDecimal leftBigDecimal = NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
				BigDecimal rightBigDecimal = NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
				return new TypedValue(leftBigDecimal.multiply(rightBigDecimal));
			}

			if (leftNumber instanceof Double || rightNumber instanceof Double) {
				if (leftNumber instanceof Double && rightNumber instanceof Double) {
					this.exitTypeDescriptor = "D";
				}
				return new TypedValue(leftNumber.doubleValue()
						* rightNumber.doubleValue());
			}

			if (leftNumber instanceof Float || rightNumber instanceof Float) {
				if (leftNumber instanceof Float && rightNumber instanceof Float) {
					this.exitTypeDescriptor = "F";
				}
				return new TypedValue(leftNumber.floatValue() * rightNumber.floatValue());
			}

			if (leftNumber instanceof Long || rightNumber instanceof Long) {
				if (leftNumber instanceof Long && rightNumber instanceof Long) {
					this.exitTypeDescriptor = "J";
				}
				return new TypedValue(leftNumber.longValue() * rightNumber.longValue());
			}
			if (leftNumber instanceof Integer && rightNumber instanceof Integer) {
				this.exitTypeDescriptor = "I";
			}
			return new TypedValue(leftNumber.intValue() * rightNumber.intValue());
		}
		else if (leftOperand instanceof String && rightOperand instanceof Integer) {
			int repeats = (Integer) rightOperand;
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < repeats; i++) {
				result.append(leftOperand);
			}
			return new TypedValue(result.toString());
		}

		return state.operate(Operation.MULTIPLY, leftOperand, rightOperand);
	}
	
	@Override
	public boolean isCompilable() {
		if (!getLeftOperand().isCompilable()) {
			return false;
		}
		if (this.children.length>1) {
			 if (!getRightOperand().isCompilable()) {
				 return false;
			 }
		}
		return this.exitTypeDescriptor!=null;
	}
	
	@Override
	public void generateCode(MethodVisitor mv, CodeFlow codeflow) {
		getLeftOperand().generateCode(mv, codeflow);
		String leftdesc = getLeftOperand().getExitDescriptor();
		if (!CodeFlow.isPrimitive(leftdesc)) {
			CodeFlow.insertUnboxInsns(mv, this.exitTypeDescriptor.charAt(0), false);
		}
		if (this.children.length>1) {
			getRightOperand().generateCode(mv, codeflow);
			String rightdesc = getRightOperand().getExitDescriptor();
			if (!CodeFlow.isPrimitive(rightdesc)) {
				CodeFlow.insertUnboxInsns(mv, this.exitTypeDescriptor.charAt(0), false);
			}
			switch (this.exitTypeDescriptor.charAt(0)) {
				case 'I':
					mv.visitInsn(IMUL);
					break;
				case 'J':
					mv.visitInsn(LMUL);
					break;
				case 'F': 
					mv.visitInsn(FMUL);
					break;
				case 'D':
					mv.visitInsn(DMUL);
					break;				
				default:
					throw new IllegalStateException("Unrecognized exit descriptor: '"+this.exitTypeDescriptor+"'");			
			}
		}
		codeflow.pushDescriptor(this.exitTypeDescriptor);
	}

}
