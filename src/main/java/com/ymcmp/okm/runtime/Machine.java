package com.ymcmp.okm.runtime;

import java.util.Map;
import java.util.List;
import java.util.Stack;
import java.util.Random;
import java.util.HashMap;
import java.util.Collections;

import java.util.stream.Collectors;

import com.ymcmp.okm.FuncBlock;

import com.ymcmp.okm.tac.*;

public class Machine {

    public final Stack<Value> callStack = new Stack<>();
    public final Map<Value, Value> locals = new HashMap<>();

    private static final Random RND = new Random();

    public Value execute(final Map<String, FuncBlock> chunk) {
        final Map<String, List<Statement>> code = chunk.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().code));
        // Call the initializer if it exists
        final List<Statement> initializer = code.get("@init");
        if (initializer != null) {
            preprocess(code);
            return execute(code, initializer);
        }
        return null;
    }

    private void preprocess(final Map<String, List<Statement>> code) {
        code.forEach((k, v) -> {
            // Reorder pop param statements
            int upperBound = 0;
            loop:
            for (int i = 0; i < v.size(); ++i) {
                switch (v.get(i).op) {
                    case POP_PARAM_INT:
                    case POP_PARAM_FLOAT:
                        ++upperBound;
                        break;
                    default:
                        break loop;
                }
            }
            Collections.reverse(v.subList(0, upperBound));
        });
    }

    private Value execute(final Map<String, List<Statement>> code, final String funcName) {
        try {
            return execute(code, code.get(funcName));
        } catch (RuntimeException ex) {
            throw new RuntimeException("RTE in stackframe of " + funcName, ex);
        }
    }

    private boolean tryCallSpecialFunctions(final String funcName, final Mutable mut) {
        switch (funcName) {
        // std.io
            case "print_int":      System.out.print(toInt(callStack.pop())); return true;
            case "println_int":    System.out.println(toInt(callStack.pop())); return true;
            case "print_long":     System.out.print(toLong(callStack.pop())); return true;
            case "println_long":   System.out.println(toLong(callStack.pop())); return true;
            case "print_double":   System.out.print(toDouble(callStack.pop())); return true;
            case "println_double": System.out.println(toDouble(callStack.pop())); return true;
            case "print_bool":     System.out.print(toBool(callStack.pop())); return true;
            case "println_bool":   System.out.println(toBool(callStack.pop())); return true;
        // std.math
            case "math_power": {
                final float exp = toFloat(callStack.pop());
                final float base = toFloat(callStack.pop());
                mut.setValue(new Fixnum(Math.pow(base, exp), 32));
                return true;
            }
            case "math_random":
                mut.setValue(new Fixnum(RND.nextInt(), 32));
                return true;
            case "math_sin":
                mut.setValue(new Fixnum(Math.sin(toFloat(callStack.pop())), 32));
                return true;
            case "math_cos":
                mut.setValue(new Fixnum(Math.cos(toFloat(callStack.pop())), 32));
                return true;
            case "math_tan":
                mut.setValue(new Fixnum(Math.tan(toFloat(callStack.pop())), 32));
                return true;
            case "math_asin":
                mut.setValue(new Fixnum(Math.asin(toFloat(callStack.pop())), 32));
                return true;
            case "math_acos":
                mut.setValue(new Fixnum(Math.acos(toFloat(callStack.pop())), 32));
                return true;
            case "math_atan":
                mut.setValue(new Fixnum(Math.atan(toFloat(callStack.pop())), 32));
                return true;
            case "math_atan2": {
                final float x = toFloat(callStack.pop());
                final float y = toFloat(callStack.pop());
                mut.setValue(new Fixnum(Math.atan2(y, x), 32));
                return true;
            }
            case "math_sinh":
                mut.setValue(new Fixnum(Math.sinh(toFloat(callStack.pop())), 32));
                return true;
            case "math_cosh":
                mut.setValue(new Fixnum(Math.cosh(toFloat(callStack.pop())), 32));
                return true;
            case "math_tanh":
                mut.setValue(new Fixnum(Math.tanh(toFloat(callStack.pop())), 32));
                return true;
            case "math_asinh":
                mut.setValue(new Fixnum(asinh(toFloat(callStack.pop())), 32));
                return true;
            case "math_acosh":
                mut.setValue(new Fixnum(acosh(toFloat(callStack.pop())), 32));
                return true;
            case "math_atanh":
                mut.setValue(new Fixnum(atanh(toFloat(callStack.pop())), 32));
                return true;
        }
        return false;
    }

    private Value execute(final Map<String, List<Statement>> code, List<Statement> func) {
        for (int i = 0; i < func.size(); ++i) {
            final Statement stmt = func.get(i);
            try {
                switch (stmt.op) {
                    case NOP:           //      <ignore>
                        // NOP does nothing..
                        break;
                    case CONV_BYTE_INT: //      dst:result, lhs:base
                    case CONV_SHORT_INT://      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)), Integer.SIZE));
                        break;
                    case CONV_LONG_INT: //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)), Integer.SIZE));
                        break;
                    case CONV_INT_BYTE: //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)), Byte.SIZE));
                        break;
                    case CONV_INT_SHORT://      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)), Short.SIZE));
                        break;
                    case CONV_INT_LONG: //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs))));
                        break;
                    case CONV_INT_FLOAT: //     dst:result, lhs:base
                    case CONV_LONG_FLOAT: //    dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs))));
                        break;
                    case CONV_FLOAT_INT: //     dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum((int) toFloat(fetchValue(stmt.lhs)), Integer.SIZE));
                        break;
                    case CONV_FLOAT_LONG: //    dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum((long) toFloat(fetchValue(stmt.lhs))));
                        break;
                    case CONV_INT_DOUBLE: //    dst:result, lhs:base
                    case CONV_LONG_DOUBLE: //   dst:result, lhs:base
                    case CONV_FLOAT_DOUBLE: //  dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs))));
                        break;
                    case CONV_DOUBLE_FLOAT: //  dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum((float) toDouble(fetchValue(stmt.lhs))));
                        break;
                    case CONV_DOUBLE_LONG: //   dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum((long) toDouble(fetchValue(stmt.lhs))));
                        break;
                    case CONV_DOUBLE_INT: //    dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum((int) toDouble(fetchValue(stmt.lhs)), Integer.SIZE));
                        break;
                    case INT_LT:        //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, makeBool(toInt(fetchValue(stmt.lhs)) < toInt(fetchValue(stmt.rhs))));
                        break;
                    case INT_GT:        //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, makeBool(toInt(fetchValue(stmt.lhs)) > toInt(fetchValue(stmt.rhs))));
                        break;
                    case INT_LE:        //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, makeBool(toInt(fetchValue(stmt.lhs)) <= toInt(fetchValue(stmt.rhs))));
                        break;
                    case INT_GE:        //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, makeBool(toInt(fetchValue(stmt.lhs)) >= toInt(fetchValue(stmt.rhs))));
                        break;
                    case INT_EQ:        //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, makeBool(toInt(fetchValue(stmt.lhs)) == toInt(fetchValue(stmt.rhs))));
                        break;
                    case INT_NE:        //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, makeBool(toInt(fetchValue(stmt.lhs)) != toInt(fetchValue(stmt.rhs))));
                        break;
                    case INT_CMP:       //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(Integer.compare(toInt(fetchValue(stmt.lhs)), toInt(fetchValue(stmt.rhs))), Integer.SIZE));
                        break;
                    case INT_NEG:       //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(-toInt(fetchValue(stmt.lhs)), Integer.SIZE));
                        break;
                    case INT_CPL:       //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(~toInt(fetchValue(stmt.lhs)), Integer.SIZE));
                        break;
                    case INT_ADD:       //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) + toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                        break;
                    case INT_SUB:       //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) - toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                        break;
                    case INT_MUL:       //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) * toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                        break;
                    case INT_DIV:       //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) / toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                        break;
                    case INT_MOD:       //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toInt(fetchValue(stmt.lhs)) % toInt(fetchValue(stmt.rhs)), Integer.SIZE));
                        break;
                    case LONG_CMP:      //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(Long.compare(toLong(fetchValue(stmt.lhs)), toLong(fetchValue(stmt.rhs))), Integer.SIZE));
                        break;
                    case LONG_NEG:      //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(-toLong(fetchValue(stmt.lhs))));
                        break;
                    case LONG_CPL:      //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(~toLong(fetchValue(stmt.lhs))));
                        break;
                    case LONG_ADD:      //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) + toLong(fetchValue(stmt.rhs))));
                        break;
                    case LONG_SUB:      //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) - toLong(fetchValue(stmt.rhs))));
                        break;
                    case LONG_MUL:      //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) * toLong(fetchValue(stmt.rhs))));
                        break;
                    case LONG_DIV:      //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) / toLong(fetchValue(stmt.rhs))));
                        break;
                    case LONG_MOD:      //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toLong(fetchValue(stmt.lhs)) % toLong(fetchValue(stmt.rhs))));
                        break;
                    case FLOAT_CMP:     //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(Float.compare(toFloat(fetchValue(stmt.lhs)), toFloat(fetchValue(stmt.rhs))), Integer.SIZE));
                        break;
                    case FLOAT_NEG:     //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(-toFloat(fetchValue(stmt.lhs)), Float.SIZE));
                        break;
                    case FLOAT_ADD:     //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) + toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                        break;
                    case FLOAT_SUB:     //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) - toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                        break;
                    case FLOAT_MUL:     //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) * toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                        break;
                    case FLOAT_DIV:     //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) / toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                        break;
                    case FLOAT_MOD:     //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toFloat(fetchValue(stmt.lhs)) % toFloat(fetchValue(stmt.rhs)), Float.SIZE));
                        break;
                    case DOUBLE_CMP:    //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(Double.compare(toDouble(fetchValue(stmt.lhs)), toDouble(fetchValue(stmt.rhs))), Integer.SIZE));
                        break;
                    case DOUBLE_NEG:    //      dst:result, lhs:base
                        locals.put(stmt.dst, new Fixnum(-toDouble(fetchValue(stmt.lhs))));
                        break;
                    case DOUBLE_ADD:    //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) + toDouble(fetchValue(stmt.rhs))));
                        break;
                    case DOUBLE_SUB:    //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) - toDouble(fetchValue(stmt.rhs))));
                        break;
                    case DOUBLE_MUL:    //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) * toDouble(fetchValue(stmt.rhs))));
                        break;
                    case DOUBLE_DIV:    //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) / toDouble(fetchValue(stmt.rhs))));
                        break;
                    case DOUBLE_MOD:    //      dst:result, lhs:a, rhs:b
                        locals.put(stmt.dst, new Fixnum(toDouble(fetchValue(stmt.lhs)) % toDouble(fetchValue(stmt.rhs))));
                        break;
                    case LOAD_TRUE:     //      dst:result
                        locals.put(stmt.dst, Fixnum.TRUE);
                        break;
                    case LOAD_FALSE:    //      dst:result
                        locals.put(stmt.dst, Fixnum.FALSE);
                        break;
                    case LOAD_NUMERAL:  //      dst:result, lhs:value
                    case LOAD_FUNC:     //      dst:store, lhs:label
                        locals.put(stmt.dst, stmt.lhs);
                    case STORE_VAR:     //      dst:store, lhs:value
                        locals.put(stmt.dst, fetchValue(stmt.lhs).duplicate());
                        break;
                    case REFER_VAR:     //      dst:store, lhs:register
                        locals.put(stmt.dst, new MutableCell(fetchValue(stmt.lhs)));
                        break;
                    case REFER_ATTR: {  //      dst:store, lhs:struct, rhs:attr
                        final StructFields struct = (StructFields) fetchValue(stmt.lhs);
                        final String attr = stmt.rhs.toString();
                        locals.put(stmt.dst, new Mutable() {

                            @Override
                            public Value duplicate() {
                                return this;
                            }

                            @Override
                            public Value getValue() {
                                return struct.get(attr);
                            }

                            @Override
                            public void setValue(Value value) {
                                struct.put(attr, value);
                            }
                        });
                        break;
                    }
                    case POINTER_GET:   //      dst:store, lhs:pointer
                        locals.put(stmt.dst, ((Mutable) fetchValue(stmt.lhs)).getValue());
                        break;
                    case POINTER_PUT:   //      dst:pointer, lhs:value
                        ((Mutable) fetchValue(stmt.dst)).setValue(fetchValue(stmt.lhs));
                        break;
                    case DEREF_GET_ATTR: //     dst:store, lhs:pointer to struct, rhs:attr
                        locals.put(stmt.dst, ((StructFields) ((Mutable) fetchValue(stmt.lhs)).getValue()).get(stmt.rhs.toString()));
                        break;
                    case DEREF_PUT_ATTR: //     dst:value, lhs:pointer to struct, rhs:attr
                        ((StructFields) ((Mutable) fetchValue(stmt.lhs)).getValue()).put(stmt.rhs.toString(), fetchValue(stmt.dst));
                        break;
                    case ALLOC_LOCAL:   //      dst:store, lhs:size of struct (we ignore this)
                        locals.put(stmt.dst, new StructFields());
                        break;
                    case ALLOC_GLOBAL: { //     dst:store, lhs:data, rhs:attr
                        StructFields fields = (StructFields) locals.get(stmt.dst);
                        if (fields == null) {
                            locals.put(stmt.dst, (fields = new StructFields()));
                        }
                        fields.put(stmt.rhs.toString(), fetchValue(stmt.lhs));
                        break;
                    }
                    case GET_ATTR:      //      dst:store, lhs:struct, rhs:attr
                        locals.put(stmt.dst, ((StructFields) fetchValue(stmt.lhs)).get(stmt.rhs.toString()));
                        break;
                    case PUT_ATTR:      //      dst:value, lhs:struct, rhs:attr
                        ((StructFields) fetchValue(stmt.lhs)).put(stmt.rhs.toString(), fetchValue(stmt.dst));
                        break;
                    case RETURN_UNIT:   //      <ignore>
                        return null;
                    case RETURN_INT:    //      dst:result
                    case RETURN_FLOAT:  //      dst:result
                        return fetchValue(stmt.dst);
                    case GOTO:          //      dst:jumpsite
                        i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        break;
                    case JUMP_INT_LT:   //      dst:jumpsite, lhs:a rhs:b
                        if (toInt(fetchValue(stmt.lhs)) < toInt(fetchValue(stmt.rhs))) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_INT_GT:   //      dst:jumpsite, lhs:a rhs:b
                        if (toInt(fetchValue(stmt.lhs)) > toInt(fetchValue(stmt.rhs))) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_INT_LE:   //      dst:jumpsite, lhs:a rhs:b
                        if (toInt(fetchValue(stmt.lhs)) <= toInt(fetchValue(stmt.rhs))) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_INT_GE:   //      dst:jumpsite, lhs:a rhs:b
                        if (toInt(fetchValue(stmt.lhs)) >= toInt(fetchValue(stmt.rhs))) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_INT_EQ:   //      dst:jumpsite, lhs:a rhs:b
                        if (toInt(fetchValue(stmt.lhs)) == toInt(fetchValue(stmt.rhs))) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_INT_NE:   //      dst:jumpsite, lhs:a rhs:b
                        if (toInt(fetchValue(stmt.lhs)) != toInt(fetchValue(stmt.rhs))) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_IF_TRUE:  //      dst:jumpsite, lhs:value
                        if (toInt(fetchValue(stmt.lhs)) != 0) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case JUMP_IF_FALSE: //      dst:jumpsite, lhs:value
                        if (toInt(fetchValue(stmt.lhs)) == 0) {
                            i = ((Label) stmt.dst).getAddress() - 1;    // -1 because loop invariant
                        }
                        break;
                    case POP_PARAM_INT: //      dst:store
                    case POP_PARAM_FLOAT: { //  dst:store
                        final Value param = callStack.pop();
                        if (stmt.dst != null) {
                            locals.put(stmt.dst, param);
                        }
                        break;
                    }
                    case PUSH_PARAM_INT: //     dst:value
                    case PUSH_PARAM_FLOAT: //   dst:value
                        // Pass by value, (including structs)
                        callStack.push(fetchValue(stmt.dst).duplicate());
                        break;
                    case CALL_NATIVE: { //      dst:name
                        final String id = stmt.dst.toString();
                        final Mutable mut = new MutableCell();
                        if (tryCallSpecialFunctions(id, mut)) {
                            return mut.getValue();
                        }
                        throw new RuntimeException("Unknown native function " + id);
                    }
                    case CALL_INT:      //      dst:store, lhs:callsite
                    case CALL_FLOAT:    //      dst:store, lhs:callsite
                        locals.put(stmt.dst, execute(code, fetchValue(stmt.lhs).toString()));
                        break;
                    case CALL_UNIT:     //      dst:callsite
                        execute(code, fetchValue(stmt.dst).toString());
                        break;
                    case TAILCALL: {    //      dst:callsite
                        func = code.get(fetchValue(stmt.dst).toString());
                        i = -1; // invariant ++i will set it to zero
                        continue;
                    }
                    default:
                        throw new RuntimeException("Unknown opcode " + stmt.op);
                }
            } catch (RuntimeException ex) {
                throw new RuntimeException("RTE at " + i + " " + stmt, ex);
            }
        }

        throw new RuntimeException("Control flowed over expected slot, return statements need to be added!");
    }

    private Value fetchValue(final Value val) {
        return locals.getOrDefault(val, val);
    }

    private static Fixnum makeBool(final boolean b) {
        return b ? Fixnum.TRUE : Fixnum.FALSE;
    }

    private static boolean toBool(final Value v) {
        return toLong(v) != 0;
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

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }

    public static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1.0));
    }

    public static double atanh(double x) {
        return 0.5 * Math.log((x + 1.0) / (x - 1.0));
    }
}