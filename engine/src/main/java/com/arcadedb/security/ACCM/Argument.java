package com.arcadedb.security.ACCM;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.logging.Level;

import com.arcadedb.log.LogManager;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;

@Data
@NoArgsConstructor
public class Argument {
    private String field;
    private ArgumentOperator operator;
    private Object value;

    // is null/missing field value treated as true or false? presumably false for now

    public Argument(String field, ArgumentOperator operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    // overload constructor for each of boolean, int, double, string, array
    public Argument(String field, ArgumentOperator operator, boolean value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public Argument(String field, ArgumentOperator operator, int value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public Argument(String field, ArgumentOperator operator, double value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public Argument(String field, ArgumentOperator operator, String value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public Argument(String field, ArgumentOperator operator, Object[] value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    // TODO add date comparison

    // TODO add support for crawling through arrays
    /**
     * Crawls the JSON object to get the value of the field, if it exists
     * @param json
     * @return
     */
    private Object getValueForFieldJsonPath(JSONObject json) {
        String[] path = field.split("\\.");
        JSONObject current = json;
        for (int i = 0; i < path.length - 1; i++) {
            if (!current.has(path[i])) {
                return null;
            }

            current = current.getJSONObject(path[i]);
        }
        return current.get(path[path.length - 1]);
    }  

    public boolean isValid() {
        return validate();
    }

    private boolean validate() {
        switch (operator) {
            case EQ:
            case NEQ:
            case ANY_OF:
                return value instanceof Object;
            case GT:
            case GT_EQ:
            case LT:
            case LT_EQ:
                return value instanceof Integer;
            case ANY_IN:
            case ALL_IN:
            case NONE_IN:
                return value instanceof Object[];
            default:
                return false;
        }
    }

    public boolean evaluate(JSONObject json) {

        if (json == null) {
            return false;
        }

        Object docFieldValue = getValueForFieldJsonPath(json);

        LogManager.instance().log(this, Level.INFO, "docFieldValue: " + docFieldValue);

        // TODO configurably handle null values- could eval to true or false
        if (docFieldValue == null) {
            return false;
        }

        // evaluate if the value satisfies the argument, and validate the value is valid for the argument type
        switch (operator) {
            case EQ:
                return this.value.equals(docFieldValue);
            case NEQ:
                return !this.value.equals(docFieldValue);
            case ANY_OF:
                for (Object val : (Object[]) this.value) {
                    LogManager.instance().log(this, Level.INFO, "val: " + val + "; vt: " + val.getClass().getName());

                    if (val.equals(docFieldValue)) {
                        return true;
                    }
                }
                return false;
            case GT:
                return (int) docFieldValue > (int) this.value;
            case GT_EQ:
                return (int) docFieldValue >= (int) this.value;
            case LT:
                return (int) docFieldValue < (int) this.value;
            case LT_EQ:
                return (int) docFieldValue <= (int) this.value;
            case ANY_IN:
                if (docFieldValue instanceof JSONArray) {
                    for (Object docVal :  ((JSONArray) docFieldValue).toList()) {

                        String str = valueToString();
                        str = str.substring(1, str.length() - 1).replace("\"", "");
                        
                        // Split the string by commas
                        String[] stringArray = str.split(", ");
                        for (String val : stringArray) {
                            if (val.equals(docVal)) {
                                return true;
                            }
                        }
                    }

                    return false;
                }

                if (docFieldValue instanceof String[]) {
                    for (String val : (String[]) this.value) {
                        if (val.equals(docFieldValue)) {
                            return true;
                        }
                    }
                } else {
                    for (Object val : (Object[]) this.value) {
                        if (val.equals(docFieldValue)) {
                            return true;
                        }
                    }
                }
                return false;
            case ALL_IN:
                for (Object val : (Object[]) this.value) {
                    if (!val.equals(docFieldValue)) {
                        return false;
                    }
                }
                return true;
            case NONE_IN:
                for (Object val : (Object[]) this.value) {
                    if (val.equals(docFieldValue)) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "Argument [field=" + field + ", operator=" + operator + ", value=" + valueToString() + "]";
    }

    private String valueToString() {
        if (this.value.getClass().isArray()) {
            if (this.value instanceof Object[]) {
                return Arrays.deepToString((Object[]) this.value);
            } else if (this.value instanceof int[]) {
                return Arrays.toString((int[]) this.value);
            } else if (this.value instanceof long[]) {
                return Arrays.toString((long[]) this.value);
            } else if (this.value instanceof byte[]) {
                return Arrays.toString((byte[]) this.value);
            } else if (this.value instanceof char[]) {
                return Arrays.toString((char[]) this.value);
            } else if (this.value instanceof float[]) {
                return Arrays.toString((float[]) this.value);
            } else if (this.value instanceof double[]) {
                return Arrays.toString((double[]) this.value);
            } else if (this.value instanceof boolean[]) {
                return Arrays.toString((boolean[]) this.value);
            } else {
                return "Unknown array type";
            }
        }
        return this.value.toString();
    }
}
