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

    @Override
    public void convert(final String name, final List<Statement> body) {
        final ArrayList<String> code = new ArrayList<>();
        code.add(name + ":");
        code.add("    push rbp        ; save old call frame");
        code.add("    mov rbp, rsp    ; initialize new call frame");

        final HashMap<Value, String> dataMapping = new HashMap<>();
        int stackOffset = 0;

        int intParam = 0;
        int floatParam = 0;

        for (int i = 0; i < body.size(); ++i) {
            final Statement stmt = body.get(i);
            code.add("  .L" + i + ":");

            switch (stmt.op) {
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
                case INT_ADD:
                    // ADD <mem>, <mem> is not allowed. Dump #lhs to eax then ADD eax, #rhs
                    code.add("    mov eax, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    add eax, " + getNumber(dataMapping, stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_SUB:
                    // SUB <mem>, <mem> is not allowed. Dump #lhs to eax then SUB eax, #rhs
                    code.add("    mov eax, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    sub eax, " + getNumber(dataMapping, stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_MUL:
                    // IMUL <mem>, <mem> is not allowed. Dump #lhs to eax then IMUL #rhs
                    code.add("    mov eax, " + getNumber(dataMapping, stmt.lhs));
                    if (stmt.rhs.isNumeric()) {
                        // IMUL <imm> is not a thing
                        final String scale = ((Fixnum) stmt.rhs).value;
                        final int k = Integer.parseInt(scale);
                        if (k > 0 && k % 2 == 0) {
                            // scale is power of 2, convert to left shifts
                            code.add("    shl eax, " + Integer.numberOfTrailingZeros(k));
                        } else {
                            // Use IMUL eax, eax, <imm> instead
                            code.add("    imul eax, eax, " + scale);
                        }
                    } else {
                        code.add("    imul " + dataMapping.get(stmt.rhs));
                    }

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_DIV:
                    // IDIV <mem>, <mem> is not allowed. Dump #lhs to eax then CDQ IDIV #rhs
                    code.add("    mov eax, " + getNumber(dataMapping, stmt.lhs));
                    code.add("    cdq");
                    if (stmt.rhs.isNumeric()) {
                        // IDIV <imm> is not a thing
                        code.add("    mov esi, " + ((Fixnum) stmt.rhs).value);
                        code.add("    idiv esi");
                    } else {
                        code.add("    imul " + dataMapping.get(stmt.rhs));
                    }

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_MOD:
                    // Same as INT_DIV, except for we care about edx not eax for output
                    code.add("    mov eax, " + dataMapping.get(stmt.lhs));
                    code.add("    cdq");
                    if (stmt.rhs.isNumeric()) {
                        code.add("    mov esi, " + ((Fixnum) stmt.rhs).value);
                        code.add("    idiv esi");
                    } else {
                        code.add("    imul " + dataMapping.get(stmt.rhs));
                    }

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", edx");
                    } else {
                        // edx is 32 bits, or 4 bytes
                        final String loc = String.format("%s [rbp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", edx");
                    }
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
                case RETURN_INT:
                    code.add("    mov " + getIntRegister(stmt.getDataSize() / 8) + ", " + getNumber(dataMapping, stmt.dst));
                    generateFuncEpilogue(code);
                    break;
                case RETURN_UNIT:
                    generateFuncEpilogue(code);
                    break;
                default:
                    code.add(stmt.toString());
                    break;
            }
        }

        sectText.add(code.stream().collect(Collectors.joining("\n")));
    }

    private static void generateFuncEpilogue(final List<String> code) {
        code.add("    ;; restore old call frame");
        code.add("    mov rsp, rbp");
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
}
