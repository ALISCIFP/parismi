package org.boris.expr.function.excel;

import org.boris.expr.Expr;
import org.boris.expr.ExprException;
import org.boris.expr.ExprString;
import org.boris.expr.IEvaluationContext;
import org.boris.expr.function.AbstractFunction;

public class ISTEXT extends AbstractFunction {
	@Override
	public Expr evaluate(IEvaluationContext context, Expr[] args) throws ExprException {
		assertArgCount(args, 1);
		Expr e = evalArg(context, args[0]);
		return bool(e instanceof ExprString);
	}
}