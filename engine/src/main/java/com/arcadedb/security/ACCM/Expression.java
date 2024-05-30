package com.arcadedb.security.ACCM;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import lombok.Data;
import lombok.NoArgsConstructor;

import com.arcadedb.log.LogManager;
import com.arcadedb.serializer.json.JSONObject;

@Data
@NoArgsConstructor
public class Expression {
    
    private String id = UUID.randomUUID().toString();
    private ExpressionOperator operator;
    private List<Expression> expressions = new ArrayList<>();
    private List<Argument> arguments = new ArrayList<>();

    public Expression(ExpressionOperator operator, Expression... expressions) {
        this.operator = operator;
        for (Expression expression : expressions) {
            this.expressions.add(expression);
        }
    }

    public Expression(ExpressionOperator operator, Argument... arguments) {
        this.operator = operator;
        for (Argument argument : arguments) {
            this.arguments.add(argument);
        }
    }

    public Expression(ExpressionOperator operator, List<Expression> expressions, List<Argument> arguments) {
        this.operator = operator;
        this.expressions = expressions;
        this.arguments = arguments;

    }

    public boolean evaluate(JSONObject json) {
        boolean result = false;

        if (operator == ExpressionOperator.AND) {
            result = true;
            for (Expression expression : expressions) {
                var expressionResult = expression.evaluate(json);
                LogManager.instance().log(this, Level.INFO, "Expression result: " + expressionResult + " for expression: " + expression);
                result = result && expressionResult;
            }
            for (Argument argument : arguments) {
                var argumentResult = argument.evaluate(json);
                LogManager.instance().log(this, Level.INFO, "Argument result: " + argumentResult + " for argument: " + argument);
                result = result && argumentResult;
            }
        } else if (operator == ExpressionOperator.OR) {
            result = false;
            for (Expression expression : expressions) {
                var expressionResult = expression.evaluate(json);
                LogManager.instance().log(this, Level.INFO, "Expression result: " + expressionResult + " for expression: " + expression);
                result = result || expressionResult;
            }
            for (Argument argument : arguments) {
                var argumentResult = argument.evaluate(json);
                LogManager.instance().log(this, Level.INFO, "Argument result: " + argumentResult + " for argument: " + argument);
                result = result || argumentResult;
            }
        }
        return result;
    }
}

