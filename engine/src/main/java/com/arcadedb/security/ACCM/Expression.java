package com.arcadedb.security.ACCM;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.arcadedb.serializer.json.JSONObject;

@Data
@NoArgsConstructor
public class Expression {
    
    private String id = UUID.randomUUID().toString();
    private ExpressionOperator operator;
    private List<Expression> expressions = new ArrayList<>();
    private List<Argument> arguments = new ArrayList<>();

    public boolean evaluate(JSONObject json) {
        boolean result = false;

        if (operator == ExpressionOperator.AND) {
            result = true;
            for (Expression expression : expressions) {
                result = result && expression.evaluate(json);
            }
            for (Argument argument : arguments) {
                result = result && argument.evaluate(json);
            }
        } else if (operator == ExpressionOperator.OR) {
            result = false;
            for (Expression expression : expressions) {
                result = result || expression.evaluate(json);
            }
            for (Argument argument : arguments) {
                result = result || argument.evaluate(json);
            }
        }
        return result;
    }
}

