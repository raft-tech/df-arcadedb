package com.arcadedb.security.ACCM;

import com.arcadedb.database.DocumentValidator;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.arcadedb.log.LogManager;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;

@Data
@NoArgsConstructor
public class Argument {
    private String field;
    private ArgumentOperator operator;
    private Object value;

    private boolean not = false;

    private boolean nullEvaluatesToGrantAccess = true;

    // is null/missing field value treated as true or false? presumably false for now

    public Argument(String field, ArgumentOperator operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public Argument(String field, ArgumentOperator operator, Object value, boolean not) {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.not = not;
    }

  //  overload constructor for each of boolean, int, double, string, array
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

    public Argument(String field, ArgumentOperator operator, List<String> value) {
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

        if (!current.has(path[path.length - 1])) {
            return null;
        }

        return current.get(path[path.length - 1]);
    }

    // TODO - remove seems like dead code.
    // public boolean isValid() {

    //     if (not) {
    //         return !validate();
    //     }
    //     return validate();
    // }

    // // TODO - remove seems like dead code.
    // private boolean validate() {
    //     switch (operator) {
    //         case EQ:
    //         case NEQ:
    //         case ANY_OF:
    //             return value instanceof Object;
    //         case CONTAINS:

    //         case GT:
    //         case GT_EQ:
    //         case LT:
    //         case LT_EQ:
    //             return value instanceof Integer;
    //         case ANY_IN:
    //         case ALL_IN:
    //         case NONE_IN:
    //             return value instanceof Object[];
    //         default:
    //             return false;
    //     }
    // }

    public boolean evaluate(JSONObject json) {
        var result = evaluateInternal(json);

        LogManager.instance().log(this, Level.INFO, "docValue: " + getValueForFieldJsonPath(json));

        if (getValueForFieldJsonPath(json) != null && isNot()) {
            result = !result;
        }

        LogManager.instance().log(this, Level.FINE, "Result: " + result + " for argument: " + this.toString() + " on json: " + json.toString(2));

        return result;
    }

    private boolean evaluateInternal(JSONObject json) {

        if (json == null) {
            return false;
        }

        Object docFieldValue = getValueForFieldJsonPath(json);

        // TODO configurably handle null values- could eval to true or false
        if (docFieldValue == null) {
            if (operator == ArgumentOperator.FIELD_NOT_PRESENT) {
                LogManager.instance().log(this, Level.FINE, "NOT PRESENT and doc value is null");
                return true;
            }

            LogManager.instance().log(this, Level.FINE, "Doc field value is null, returning null handling: " + this.nullEvaluatesToGrantAccess);
            return this.nullEvaluatesToGrantAccess;
        } else if (operator == ArgumentOperator.FIELD_NOT_PRESENT) {
            LogManager.instance().log(this, Level.FINE, "NOT PRESENT and doc value is NOT null");
            return false;
        }

        // evaluate if the value satisfies the argument, and validate the value is valid for the argument type
        switch (operator) {
            case EQ:
                return this.value.equals(docFieldValue);
            case NEQ:
                return !this.value.equals(docFieldValue);
            case ANY_OF:
                // check if this.value is a list
                if (this.value instanceof List) {
                    for (Object val : (List<Object>) this.value) {
                        if (val.equals(docFieldValue)) {
                            return true;
                        }
                    }
                    return false;
                }

                // check if value is a string encoded array, and not an array of strings
                if (this.value instanceof String) {
                    String str = (String) this.value;
                    str = str.substring(1, str.length() - 1).replace("\"", "");
                    String[] stringArray = str.split(", ");

                    for (String val : stringArray) {
                        if (val.equals(docFieldValue)) {
                            return true;
                        }
                    }
                    return false;
                }

                for (Object val : (Object[]) this.value) {
                    LogManager.instance().log(this, Level.FINE, "val: " + val + "; vt: " + val.getClass().getName());

                    if (val.equals(docFieldValue)) {
                        return true;
                    }
                }
                return false;

            case CONTAINS:
                return valueContains(docFieldValue);
            case NOT_CONTAINS:
                return !valueContains(docFieldValue);
            case GT: {
                if (this.value instanceof String) {
                    return DocumentValidator.classificationOptions.get((String) docFieldValue) > DocumentValidator.classificationOptions.get((String) this.value);
                } else {
                    return (int) docFieldValue > (int) this.value;
                }
            }
            case GT_EQ: {
                if (this.value instanceof String) {
                    return DocumentValidator.classificationOptions.get((String) docFieldValue) >= DocumentValidator.classificationOptions.get((String) this.value);
                } else {
                    return (int) docFieldValue > (int) this.value;
                }
            }
            case LT: {
                if (this.value instanceof String) {
                    return DocumentValidator.classificationOptions.get((String) docFieldValue) < DocumentValidator.classificationOptions.get((String) this.value);
                } else {
                    return (int) docFieldValue > (int) this.value;
                }
            }
            case LT_EQ: {
                if (this.value instanceof String) {
                    return DocumentValidator.classificationOptions.get((String) docFieldValue) <= DocumentValidator.classificationOptions.get((String) this.value);
                } else {
                    return (int) docFieldValue > (int) this.value;
                }
            }
            case ANY_IN:
                if (docFieldValue instanceof JSONArray) {
                    for (Object docVal :  ((JSONArray) docFieldValue).toList()) {

                        String str = valueToString();
                        str = str.substring(1, str.length() - 1).replace("\"", "");
                        
                        // Split the string by commas
                        String[] stringArray = str.split(", ");

                        LogManager.instance().log(this, Level.FINE, "stringArray: " + stringArray);
                        LogManager.instance().log(this, Level.FINE, "docVal: " + docVal);

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
                List<String> docValList = new ArrayList<>();
                List<String> listToCheck = new ArrayList<>();

                if (docFieldValue instanceof JSONArray) {
                    docValList = ((JSONArray) docFieldValue).toList().stream().map(Object::toString).collect(Collectors.toList());
                }

                if (docFieldValue instanceof String[]) {
                    docValList = Arrays.asList((String[]) docFieldValue);
                }

                if (this.value instanceof List) {
                    List<?> list = (List<?>) this.value;
                    for (Object element : list) {
                        if (element instanceof String) {
                            listToCheck.add((String) element);
                        }
                    }
                }

                if (this.value instanceof String) {
                    String str = (String) this.value;
                    str = str.substring(1, str.length() - 1).replace("\"", "");
                    String[] stringArray = str.split(",");
                    listToCheck = Arrays.asList(stringArray).stream().map(String::trim).collect(Collectors.toList());
                }

                if (this.value instanceof String[]) {
                    listToCheck = Arrays.asList((String[]) this.value).stream().map(String::trim).collect(Collectors.toList());
                }

                return listToCheck.containsAll(docValList);
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

    private boolean valueContains(Object docFieldValue){
        if (docFieldValue instanceof JSONArray) {
            for (Object docVal :  ((JSONArray) docFieldValue).toList()) {

                String str = valueToString();
                str = str.substring(1, str.length() - 1).replace("\"", "");

                // Split the string by commas
                String[] stringArray = str.split(",");

                LogManager.instance().log(this, Level.FINE, "Evaluation Values: " + Arrays.toString(stringArray));
                LogManager.instance().log(this, Level.FINE, "Doc Value: " + docVal);

                for (String val : stringArray) {
                    if (val.equals(docVal)) {
                        LogManager.instance().log(this, Level.INFO, "match found");
                        return true;
                    }
                }
            }
            LogManager.instance().log(this, Level.INFO, "broke out1");
            return false;
        }
        LogManager.instance().log(this, Level.INFO, "broke out2");
        return false;
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
