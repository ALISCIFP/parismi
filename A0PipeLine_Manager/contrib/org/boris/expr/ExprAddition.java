/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 * Peter Smith
 *******************************************************************************/
package org.boris.expr;

public class ExprAddition extends AbstractMathematicalOperator {
	public ExprAddition(Expr lhs, Expr rhs) {
		super(ExprType.Addition, lhs, rhs);
	}

	@Override
	protected Expr evaluate(double lhs, double rhs) throws ExprException {
		return new ExprDouble(lhs + rhs);
	}

	@Override
	public void validate() throws ExprException {
		if (lhs != null)
			lhs.validate();
		if (rhs == null)
			throw new ExprException("RHS of operator missing");
		rhs.validate();
	}

	@Override
	public String toString() {
		if (lhs == null)
			return rhs.toString();
		else
			return lhs + "+" + rhs;
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return new ExprAddition((Expr) lhs.clone(), (Expr) rhs.clone());
	}

}