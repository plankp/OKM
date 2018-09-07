package com.ymcmp.okm.runtime;

import java.util.Map;
import java.util.List;
import java.util.Stack;
import java.util.HashMap;

import com.ymcmp.okm.tac.*;

public class Machine {

    public final Stack<Value> callStack = new Stack<>();
    public final Map<Value, Value> locals = new HashMap<>();

    public Value execute(final Map<String, List<Statement>> code) {
        // Call the initializer if it exists
        final List<Statement> initializer = code.get("@init");
        if (initializer != null) {
            return execute(code, initializer);
        }
        return null;
    }

    private Value execute(final Map<String, List<Statement>> code, final String funcName) {
        final Mutable<Value> mut = new Mutable<>();
        if (tryCallSpecialFunctions(funcName, mut)) {
            return mut.getValue();
        }
        return execute(code, code.get(funcName));
    }

    private boolean tryCallSpecialFunctions(final String funcName, final Mutable<Value> mut) {
        if (funcName.startsWith("@M")) {
            switch (funcName.substring(4)) {
            // std.io
                case "print:i:":    System.out.print(toInt(callStack.pop())); return true;
                case "println:i:":  System.out.println(toInt(callStack.pop())); return true;
            // std.math
                case "atan2:y:x:":      mut.setValue(new Fixnum(Math.atan2(toDouble(callStack.pop()), toDouble(callStack.pop())))); return true;
                case "asin:x:":         mut.setValue(new Fixnum(Math.asin(toDouble(callStack.pop())))); return true;
                case "acos:x:":         mut.setValue(new Fixnum(Math.acos(toDouble(callStack.pop())))); return true;
                case "atan:x:":         mut.setValue(new Fixnum(Math.atan(toDouble(callStack.pop())))); return true;
                case "sin:rad:":        mut.setValue(new Fixnum(Math.sin(toDouble(callStack.pop())))); return true;
                case "cos:rad:":        mut.setValue(new Fixnum(Math.cos(toDouble(callStack.pop())))); return true;
                case "tan:rad:":        mut.setValue(new Fixnum(Math.tan(toDouble(callStack.pop())))); return true;
                case "sinh:x:":         mut.setValue(new Fixnum(Math.sinh(toDouble(callStack.pop())))); return true;
                case "cosh:x:":         mut.setValue(new Fixnum(Math.cosh(toDouble(callStack.pop())))); return true;
                case "tanh:x:":         mut.setValue(new Fixnum(Math.tanh(toDouble(callStack.pop())))); return true;
                case "power:base:exp:": mut.setValue(new Fixnum(Math.pow(toDouble(callStack.pop()), toDouble(callStack.pop())))); return true;
                case "random:":         mut.setValue(new Fixnum(Math.random())); return true;
            }
        }
        return false;
    }

    private Value execute(final Map<String, List<Statement>> code, List<Statement> func) {
        for (int i = 0; i < func.size(); ++i) {
            final Statement stmt = func.get(i);
            switch (stmt.op) {
                case NOP:           // NOP              <ignore>
                    // NOP does nothing..
                    break;
                case CONV_BYTE_INT: // CONV_BYTE_INT    dst:result, lhs:base
                case CONV_SHORT_INT:// CONV_SHORT_INT   dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs))));
                    break;
                case CONV_LONG_INT: // CONV_LONG_INT    dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)), Integer.SIZE));
                    break;
                case CONV_INT_BYTE: // CONV_INT_BYTE    dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)), Byte.SIZE));
                    break;
                case CONV_INT_SHORT:// CONV_INT_SHORT   dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)), Short.SIZE));
                    break;
                case CONV_INT_LONG: // CONV_INT_LONG    dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs))));
                    break;
                case CONV_INT_DOUBLE: //                dst:result, lhs:base
                case CONV_LONG_DOUBLE: //               dst:result, lhs:base
                case CONV_FLOAT_DOUBLE: //              dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs))));
                    break;
                case INT_NEG:       // INT_NEG          dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(-toInt(fetchValue(stmt.lhs))));
                    break;
                case INT_CPL:       // INT_CPL          dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(~toInt(fetchValue(stmt.lhs))));
                    break;
                case INT_ADD:       // INT_ADD          dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) + toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                    break;
                case INT_SUB:       // INT_SUB          dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) - toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                    break;
                case INT_MUL:       // INT_MUL          dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) * toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                    break;
                case INT_DIV:       // INT_DIV          dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) / toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                    break;
                case INT_MOD:       // INT_MOD          dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) % toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                    break;
                case LONG_NEG:      // LONG_NEG         dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(-toLong(fetchValue(stmt.lhs))));
                    break;
                case LONG_CPL:      // LONG_CPL         dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(~toLong(fetchValue(stmt.lhs))));
                    break;
                case LONG_ADD:      // LONG_ADD         dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) + toLong(fetchValue(stmt.rhs))));
                    break;
                case LONG_SUB:      // LONG_SUB         dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) - toLong(fetchValue(stmt.rhs))));
                    break;
                case LONG_MUL:      // LONG_MUL         dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) * toLong(fetchValue(stmt.rhs))));
                    break;
                case LONG_DIV:      // LONG_DIV         dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) / toLong(fetchValue(stmt.rhs))));
                    break;
                case LONG_MOD:      // LONG_MOD         dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) % toLong(fetchValue(stmt.rhs))));
                    break;
                case FLOAT_NEG:     // FLOAT_NEG        dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(-toFloat(fetchValue(stmt.lhs)), Float.SIZE));
                    break;
                case FLOAT_ADD:     // FLOAT_ADD        dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) + toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                    break;
                case FLOAT_SUB:     // FLOAT_SUB        dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) - toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                    break;
                case FLOAT_MUL:     // FLOAT_MUL        dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) * toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                    break;
                case FLOAT_DIV:     // FLOAT_DIV        dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) / toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                    break;
                case FLOAT_MOD:     // FLOAT_MOD        dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) % toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                    break;
                case DOUBLE_NEG:    // DOUBLE_NEG       dst:result, lhs:base
                    locals.put(stmt.dst, new Fixnum(-toDouble(fetchValue(stmt.lhs))));
                    break;
                case DOUBLE_ADD:    // DOUBLE_ADD       dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) + toDouble(fetchValue(stmt.rhs))));
                    break;
                case DOUBLE_SUB:    // DOUBLE_SUB       dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) - toDouble(fetchValue(stmt.rhs))));
                    break;
                case DOUBLE_MUL:    // DOUBLE_MUL       dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) * toDouble(fetchValue(stmt.rhs))));
                    break;
                case DOUBLE_DIV:    // DOUBLE_DIV       dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) / toDouble(fetchValue(stmt.rhs))));
                    break;
                case DOUBLE_MOD:    // DOUBLE_MOD       dst:result, lhs:a, rhs:b
                    locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) % toDouble(fetchValue(stmt.rhs))));
                    break;
                case LOAD_TRUE:     // LOAD_TRUE        dst:result
                    locals.put(stmt.dst, Fixnum.TRUE);
                    break;
                case LOAD_FALSE:    // LOAD_FALSE       dst:result
                    locals.put(stmt.dst, Fixnum.FALSE);
                    break;
                case LOAD_NUMERAL:  // LOAD_NUMERAL     dst:result, lhs:value
                    locals.put(stmt.dst, stmt.lhs);
                    break;
                case STORE_VAR:     // STORE_VAR        dst:store, lhs:value
                    locals.put(stmt.dst, fetchValue(stmt.lhs));
                    break;
                case RETURN_UNIT:   // RETURN_UNIT
                    return null;
                case RETURN_VALUE:  // RETURN_VALUE     dst:result
                    return fetchValue(stmt.dst);
                case GOTO:          // GOTO             dst:jumpsite
                    i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                    break;
                case JUMP_IF_TRUE:  // JUMP_IF_TRUE     dst:jumpsite, lhs:value
                    if (toInt(fetchValue(stmt.lhs)) != 0) {
                        i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                    }
                    break;
                case JUMP_IF_FALSE: // JUMP_IF_FALSE    dst:jumpsite, lhs:value
                    if (toInt(fetchValue(stmt.lhs)) == 0) {
                        i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                    }
                    break;
                case POP_PARAM:     // POP_PARAM        dst:store
                    locals.put(stmt.dst, callStack.pop());
                    break;
                case PUSH_PARAM:    // PUSH_PARAM       dst:value
                    callStack.push(fetchValue(stmt.dst));
                    break;
                case CALL:          // CALL             dst:store, lhs:callsite
                    locals.put(stmt.dst, execute(code, stmt.lhs.toString()));
                    break;
                case TAILCALL: {    // TAILCALL         dst:callsite
                    // Update parameter $list, reset counter $i and restart
                    // unless it is one of the special functions
                    final String funcName = stmt.dst.toString();
                    final Mutable<Value> mut = new Mutable<>();
                    if (tryCallSpecialFunctions(funcName, mut)) {
                        return mut.getValue();
                    }

                    func = code.get(funcName);
                    i = -1; // invariant ++i will set it to zero
                    continue;
                }
                default: {
                    System.err.println("Undefined execute " + stmt.op);
                }
            }
        }

        throw new RuntimeException("Control flowed over expected slot, return statements need to be added!");
    }

    private Value fetchValue(final Value val) {
        return locals.getOrDefault(val, val);
    }

    private static int toInt(final Value v) {
        final Fixnum f = (Fixnum) v;
        if (f.isInt && f.size <= Integer.SIZE) {
            return Integer.parseInt(f.value);
        }
        throw new RuntimeException("Value " + f + " does not conform to int");
    }

    private static long toLong(final Value v) {
        final Fixnum f = (Fixnum) v;
        if (f.isInt && f.size <= Long.SIZE) {
            return Long.parseLong(f.value);
        }
        throw new RuntimeException("Value " + f + " does not conform to long");
    }

    private static float toFloat(final Value v) {
        return Float.parseFloat(((Fixnum) v).value);
    }

    private static double toDouble(final Value v) {
        return Double.parseDouble(((Fixnum) v).value);
    }
}