package com.arcadedb.security.ACCM;

import lombok.Data;
import lombok.NoArgsConstructor;
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

        Object value = getValueForFieldJsonPath(json);

        // TODO configurably handle null values- could eval to true or false
        if (value == null) {
            return false;
        }

        // evaluate if the value satisfies the argument, and validate the value is valid for the argument type
        switch (operator) {
            case EQ:
                return this.value.equals(value);
            case NEQ:
                return !this.value.equals(value);
            case ANY_OF:
                for (Object val : (Object[]) this.value) {
                    if (val.equals(value)) {
                        return true;
                    }
                }
                return false;
            case GT:
                return (int) value > (int) this.value;
            case GT_EQ:
                return (int) value >= (int) this.value;
            case LT:
                return (int) value < (int) this.value;
            case LT_EQ:
                return (int) value <= (int) this.value;
            case ANY_IN:
                for (Object val : (Object[]) this.value) {
                    if (val.equals(value)) {
                        return true;
                    }
                }
                return false;
            case ALL_IN:
                for (Object val : (Object[]) this.value) {
                    if (!val.equals(value)) {
                        return false;
                    }
                }
                return true;
            case NONE_IN:
                for (Object val : (Object[]) this.value) {
                    if (val.equals(value)) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }
}
