package com.ymcmp.okm.converter;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Fixnum;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public class AMD64Converter implements Converter {

    private static class DataValue {

        public final String label;
        public final String value;

        public DataValue(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String output() {
            return label + " " + value;
        }
    }

    private final Map<Fixnum, DataValue> sectData = new HashMap<>();
    private final List<String> sectText = new ArrayList<>();

    private final HashMap<Value, String> dataMapping = new HashMap<>();
    private int stackOffset;

    @Override
    public void reset() {
        sectData.clear();
        sectText.clear();
    }

    @Override
    public String getResult() {
        return Stream.concat(
                Stream.concat(Stream.of("section .data"), sectData.values().stream().map(DataValue::output)),
                Stream.concat(Stream.of("section .text"), sectText.stream()))
                .collect(Collectors.joining("\n\n"));
    }

    private static String mangleName(final String name) {
        final String subst = name.substring(1);
        return "_F" + (subst.indexOf(':') + 1) + "_" + subst.replace(":", "_");
    }

    @Override
    public void convert(final String name, final List<Statement> body) {
        final ArrayList<String> code = new ArrayList<>();
        code.add(mangleName(name) + ":");
        code.add("    push rbp        ; save old call frame");
        code.add("    mov rbp, rsp    ; initialize new call frame");

        dataMapping.clear();
        stackOffset = 0;

        int intParam = 0;
        int floatParam = 0;

        for (int i = 0; i < body.size(); ++i) {
            final Statement stmt = body.get(i);
            code.add("  .L" + i + ":");

            switch (stmt.op) {
                case CONV_BYTE_INT:
                case CONV_SHORT_INT:
                    code.add("    movsx edi, " + getNumber(dataMapping, stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", edi");
                    } else {
                        // int is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", edi");
                    }
                    break;
                case CONV_INT_LONG:
                    code.add("    movsxd rdi, " + getNumber(dataMapping, stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", rdi");
                    } else {
                        // long is 64 bits, or 8 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(8), (stackOffset += 8));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", rdi");
                    }
                    break;
                case CONV_INT_BYTE:
                    code.add("    mov edi, " + getNumber(dataMapping, stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", dil");
                    } else {
                        // byte is 1 byte
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(1), (stackOffset += 1));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", dil");
                    }
                    break;
                case CONV_INT_SHORT:
                    code.add("    mov edi, " + getNumber(dataMapping, stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", di");
                    } else {
                        // short is 2 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(2), (stackOffset += 2));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", di");
                    }
                    break;
                case CONV_LONG_INT:
                    code.add("    mov rdi, " + getNumber(dataMapping, stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", edi");
                    } else {
                        // int is 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", edi");
                    }
                    break;
                case FLOAT_ADD:
                    // ADDSS <mem>, <mem> is not allowed. Dump #lhs to xmm0 then ADDSS xmm0, #rhs
                    code.add("    movss xmm0, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    addss xmm0, " + getNumber(dataMapping, stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    movss " + dataMapping.get(stmt.dst) + ", xmm0");
                    } else {
                        // float is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    movss " + loc + ", xmm0");
                    }
                    break;
                case FLOAT_SUB:
                    // SUBSS <mem>, <mem> is not allowed. Dump #lhs to xmm0 then SUBSS xmm0, #rhs
                    code.add("    movss xmm0, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    subss xmm0, " + getNumber(dataMapping, stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    movss " + dataMapping.get(stmt.dst) + ", xmm0");
                    } else {
                        // float is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    movss " + loc + ", xmm0");
                    }
                    break;
                case FLOAT_MUL:
                    // MULSS <mem>, <mem> is not allowed. Dump #lhs to xmm0 then MULSS xmm0, #rhs
                    code.add("    movss xmm0, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    mulss xmm0, " + getNumber(dataMapping, stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    movss " + dataMapping.get(stmt.dst) + ", xmm0");
                    } else {
                        // float is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    movss " + loc + ", xmm0");
                    }
                    break;
                case FLOAT_DIV:
                    // DIVSS <mem>, <mem> is not allowed. Dump #lhs to xmm0 then DIVSS xmm0, #rhs
                    code.add("    movss xmm0, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    divss xmm0, " + getNumber(dataMapping, stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    movss " + dataMapping.get(stmt.dst) + ", xmm0");
                    } else {
                        // float is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    movss " + loc + ", xmm0");
                    }
                    break;
                case FLOAT_MOD:
                    // use x87 instruction set for now, could switch to float fmodf(float, float) in C <math.h>
                    code.add("    ;; ST(1) <- rhs");
                    code.add("    fld " + getNumber(dataMapping, stmt.rhs));
                    code.add("    ;; ST(0) <- lhs");
                    code.add("    fld " + getNumber(dataMapping, stmt.lhs));
                    code.add("    ;; ST(0) <- ST(0) % ST(1)");
                    code.add("    fprem");

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    fstp " + dataMapping.get(stmt.dst));
                    } else {
                        // float is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    fstp " + loc);
                    }
                    break;
                case LONG_ADD:
                    genericAdd(true, code, stmt);
                    break;
                case LONG_SUB:
                    genericSub(true, code, stmt);
                    break;
                case LONG_MUL:
                    genericMul(true, code, stmt);
                    break;
                case LONG_DIV:
                    genericDiv(true, "rax", code, stmt);
                    break;
                case LONG_MOD:
                    genericDiv(true, "rdx", code, stmt);
                    break;
                case INT_ADD:
                    genericAdd(false, code, stmt);
                    break;
                case INT_SUB:
                    genericSub(false, code, stmt);
                    break;
                case INT_MUL:
                    genericMul(false, code, stmt);
                    break;
                case INT_DIV:
                    genericDiv(false, "eax", code, stmt);
                    break;
                case INT_MOD:
                    genericDiv(false, "edx", code, stmt);
                    break;
                case POP_PARAM_FLOAT: {
                    final int bs = stmt.getDataSize() / 8;
                    String dst;
                    if (floatParam < 8) {
                        dst = String.format("%s [rbp - %d]", toWordSizeString(bs), (stackOffset = roundToNextDivisible(stackOffset, bs)));
                        code.add("    " + (bs == 4 ? "movss" : "movsd") + " " + dst + ", " + getFloatRegParam(floatParam));
                    } else {
                        // Leave the data on the stack for now
                        dst = String.format("%s [rbp + %d]", toWordSizeString(bs), 16 + 8 * (floatParam - 8));
                    }

                    dataMapping.put(stmt.dst, dst);
                    ++floatParam;
                    break;
                }
                case POP_PARAM_INT: {
                    final int bs = stmt.getDataSize() / 8;
                    String dst;
                    if (intParam < 6) {
                        dst = String.format("%s [rbp - %d]", toWordSizeString(bs), (stackOffset = roundToNextDivisible(stackOffset, bs)));
                        code.add("    mov " + dst + ", " + getIntRegParam(intParam, bs));
                    } else {
                        // Leave the data on the stack for now
                        dst = String.format("%s [rbp + %d]", toWordSizeString(bs), 16 + 8 * (intParam - 6));
                    }

                    dataMapping.put(stmt.dst, dst);
                    ++intParam;
                    break;
                }
                case RETURN_FLOAT:
                    code.add("    " + (stmt.getDataSize() / 8 == 4 ? "movss" : "movsd") + " xmm0, " + getNumber(dataMapping, stmt.dst));
                    generateFuncEpilogue(code);
                    break;
                case RETURN_INT: {
                    final String src = getNumber(dataMapping, stmt.dst);
                    switch (stmt.getDataSize() / 8) {
                        case 1:
                            code.add("    movsx eax, " + src);
                            break;
                        case 2:
                            code.add("    mov ax, " + src);
                            code.add("    cwde");
                            break;
                        case 4:
                            code.add("    mov eax, " + src);
                            break;
                        case 8:
                            code.add("    mov rax, " + src);
                            break;
                        default:
                            throw new AssertionError("Unknown data size " + (stmt.getDataSize() / 8));
                    }
                    generateFuncEpilogue(code);
                    break;
                }
                case RETURN_UNIT:
                    generateFuncEpilogue(code);
                    break;
                default:
                    code.add(stmt.toString());
                    break;
            }
        }

        // If stack is more than 128 bytes (red-zone), need relocate rsp
        if (stackOffset > 128) {
            final int relocate = stackOffset - 128;
            code.add(2, "    sub rsp, " + relocate);
            code.add(code.size() - 2, "    add rsp, " + relocate);
        }
        sectText.add(code.stream().collect(Collectors.joining("\n")));
    }

    private static void generateFuncEpilogue(final List<String> code) {
        code.add("    ;; restore old call frame");
        code.add("    pop rbp");
        code.add("    ret");
    }

    private static String toWordSizeString(int size) {
        switch (size) {
            case 1: return "BYTE";
            case 2: return "WORD";
            case 4: return "DWORD";
            case 8: return "QWORD";
        }
        throw new AssertionError("Invalid word size " + size);
    }

    private static String toDataSizeString(int size) {
        switch (size) {
            case 1: return "db";
            case 2: return "dw";
            case 4: return "dd";
            case 8: return "dq";
        }
        throw new AssertionError("Invalid word size " + size);
    }

    private static int roundToNextDivisible(int a, int b) {
        return ((int) ((a + b - 1) / b) + 1) * b;
    }

    private String getNumber(Map<Value, String> mapping, Value v) {
        if (v.isNumeric()) {
            final Fixnum num = (Fixnum) v;
            if (num.isInt) {
                return num.value;
            }

            // It is float, which needs to be read from data section
            final int bs = num.size / 8;
            if (sectData.containsKey(num)) {
                return toWordSizeString(bs) + " [rel " + sectData.get(num).label + "]";
            }
            final String label = "_K" + sectData.size();
            sectData.put(num, new DataValue(label, toDataSizeString(bs) + " " + num.value));
            // Explicit relative addressing!
            return toWordSizeString(bs) + " [rel " + label + "]";
        }
        return mapping.get(v);
    }

    private static String getIntRegister(int size) {
        switch (size) {
            case 1: return "al";
            case 2: return "ax";
            case 4: return "eax";
            case 8: return "rax";
        }
        throw new AssertionError("Impossible int register with size " + size);
    }

    private static String getIntRegParam(int idx, int size) {
        switch (idx) {
            case 0:
                switch (size) {
                    case 1: return "dil";
                    case 2: return "di";
                    case 4: return "edi";
                    case 8: return "rdi";
                }
                break;
            case 1:
                switch (size) {
                    case 1: return "sil";
                    case 2: return "si";
                    case 4: return "esi";
                    case 8: return "rsi";
                }
                break;
            case 2:
                switch (size) {
                    case 1: return "dl";
                    case 2: return "dx";
                    case 4: return "edx";
                    case 8: return "rdx";
                }
                break;
            case 3:
                switch (size) {
                    case 1: return "cl";
                    case 2: return "cx";
                    case 4: return "ecx";
                    case 8: return "rcx";
                }
                break;
            case 4:
                switch (size) {
                    case 1: return "r8b";
                    case 2: return "r8w";
                    case 4: return "r8d";
                    case 8: return "r8";
                }
                break;
            case 5:
                switch (size) {
                    case 1: return "r9b";
                    case 2: return "r9w";
                    case 4: return "r9d";
                    case 8: return "r9";
                }
                break;
        }
        throw new AssertionError("Invalid param slot or size: " + idx + "," + size);
    }

    private static String getFloatRegParam(int idx) {
        switch (idx) {
            case 0: return "xmm0";
            case 1: return "xmm1";
            case 2: return "xmm2";
            case 3: return "xmm3";
            case 4: return "xmm4";
            case 5: return "xmm5";
            case 6: return "xmm6";
            case 7: return "xmm7";
        }
        throw new AssertionError("Invalid param slot: " + idx);
    }

    private void genericAdd(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(dataMapping, stmt.lhs));
        code.add("    add " + accum + ", " + getNumber(dataMapping, stmt.rhs));

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + accum);
        } else {
            final String loc = String.format("%s [rbp - %d]", toWordSizeString(bs), (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void genericSub(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(dataMapping, stmt.lhs));
        code.add("    sub " + accum + ", " + getNumber(dataMapping, stmt.rhs));

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + accum);
        } else {
            final String loc = String.format("%s [rbp - %d]", toWordSizeString(bs), (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void genericMul(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov "+ accum + ", " + getNumber(dataMapping, stmt.lhs));
        if (stmt.rhs.isNumeric()) {
            // IMUL <imm> is not a thing
            final String scale = ((Fixnum) stmt.rhs).value;
            final long k = Long.parseLong(scale);
            if (k != 0 && k % 2 == 0) {
                // scale is power of 2, convert to left shifts
                code.add("    shl " + accum + ", " + Long.numberOfTrailingZeros(k));
                if (k < 0) {
                    // negate result
                    code.add("    neg " + accum);
                }
            } else if (eightBytes && (k > Integer.MAX_VALUE || k < Integer.MIN_VALUE)) {
                // IMUL <reg>, <reg>, <imm> does not work because imm only
                // takes ints or smaller. Dump scale into another register
                // and then do IMUL <reg>, <reg>
                code.add("    mov rsi, " + scale);
                code.add("    imul " + accum + ", rsi");
            } else {
                // Use IMUL <reg>, <reg>, <imm> instead
                code.add("    imul " + accum + ", " + accum + ", " + scale);
            }
        } else {
            code.add("    imul " + dataMapping.get(stmt.rhs));
        }

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + accum);
        } else {
            final String loc = String.format("%s [rbp - %d]", toWordSizeString(bs), (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void genericDiv(boolean eightBytes, String resultReg, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);

        code.add("    mov " + accum + ", " + getNumber(dataMapping, stmt.lhs));
        code.add("    " + (eightBytes ? "cqo" : "cdq"));
        if (stmt.rhs.isNumeric()) {
            // IDIV <imm> is not a thing
            final String scale = ((Fixnum) stmt.rhs).value;
            final String tmp = eightBytes ? "rsi" : "esi";
            code.add("    mov " + tmp + ", " + scale);
            code.add("    idiv " + tmp);
        } else {
            code.add("    idiv " + dataMapping.get(stmt.rhs));
        }

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + resultReg);
        } else {
            final String loc = String.format("%s [rbp - %d]", toWordSizeString(bs), (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + resultReg);
        }
    }
}
