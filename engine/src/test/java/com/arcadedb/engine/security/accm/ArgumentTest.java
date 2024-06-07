package com.arcadedb.engine.security.accm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.security.ACCM.Argument;
import com.arcadedb.security.ACCM.ArgumentOperator;

public class ArgumentTest {

    @Test
    public void testEqualityOperator() {
        Argument argument = new Argument("field", ArgumentOperator.EQ, "value");
        boolean result = argument.evaluate(createJsonObject("field", "value"));
        Assertions.assertTrue(result);
    }

    @Test
    public void testInequalityOperator() {
        Argument argument = new Argument("field", ArgumentOperator.NEQ, "value");
        boolean result = argument.evaluate(createJsonObject("field", "value"));
        Assertions.assertFalse(result);
    }

    @Test
    public void testAnyOfOperator() {
        Argument argument = new Argument("field", ArgumentOperator.ANY_OF, new Object[]{"value1", "value2"});
        boolean result = argument.evaluate(createJsonObject("field", "value1"));
        Assertions.assertTrue(result);
    }

    // Add more test cases for other operators

    private JSONObject createJsonObject(String field, Object value) {
        JSONObject json = new JSONObject();
        json.put(field, value);
        return json;
    }
}