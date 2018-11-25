package com.ymcmp.okm.converter;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.ymcmp.okm.tac.Label;
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
            return label + ":\n    " + value;
        }
    }

    private static final String SECTION_DATA_HEADER =
            "section .data\n" +
            "align 16\n" +
            "CC0 dd 2147483648,0,0,0\n" +
            "CC1 dd 0,-2147483648,0,0\n";

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
                Stream.concat(Stream.of(SECTION_DATA_HEADER), sectData.values().stream().map(DataValue::output)),
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
                case NOP:
                    // Does nothing!
                    break;
                case CONV_BYTE_INT:
                case CONV_SHORT_INT:
                    code.add("    movsx edi, " + getNumber(stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", edi");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", edi");
                    }
                    break;
                case CONV_INT_LONG:
                    code.add("    movsxd rdi, " + getNumber(stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", rdi");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 8));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", rdi");
                    }
                    break;
                case CONV_INT_BYTE:
                    code.add("    mov edi, " + getNumber(stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", dil");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 1));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", dil");
                    }
                    break;
                case CONV_INT_SHORT:
                    code.add("    mov edi, " + getNumber(stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", di");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 2));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", di");
                    }
                    break;
                case CONV_LONG_INT:
                    code.add("    mov rdi, " + getNumber(stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", edi");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", edi");
                    }
                    break;
                case CONV_INT_FLOAT:
                    int2Float(false, false, code, stmt);
                    break;
                case CONV_LONG_FLOAT:
                    int2Float(true, false, code, stmt);
                    break;
                case CONV_INT_DOUBLE:
                    int2Float(false, true, code, stmt);
                    break;
                case CONV_LONG_DOUBLE:
                    int2Float(true, true, code, stmt);
                    break;
                case CONV_FLOAT_DOUBLE:
                    code.add("    movss xmm0, " + getNumber(stmt.lhs));
                    code.add("    cvtss2sd xmm0, xmm0");

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    movsd " + dataMapping.get(stmt.dst) + ", xmm0");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 8));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    movsd " + loc + ", xmm0");
                    }
                    break;
                case DOUBLE_ADD:
                    floatSSEMath(true, "add", code, stmt);
                    break;
                case DOUBLE_SUB:
                    floatSSEMath(true, "sub", code, stmt);
                    break;
                case DOUBLE_MUL:
                    floatSSEMath(true, "mul", code, stmt);
                    break;
                case DOUBLE_DIV:
                    floatSSEMath(true, "div", code, stmt);
                    break;
                case DOUBLE_MOD:
                    floatFprem(true, code, stmt);
                    break;
                case DOUBLE_NEG:
                    floatNegate(true, code, stmt);
                    break;
                case FLOAT_ADD:
                    floatSSEMath(false, "add", code, stmt);
                    break;
                case FLOAT_SUB:
                    floatSSEMath(false, "sub", code, stmt);
                    break;
                case FLOAT_MUL:
                    floatSSEMath(false, "mul", code, stmt);
                    break;
                case FLOAT_DIV:
                    floatSSEMath(false, "div", code, stmt);
                    break;
                case FLOAT_MOD:
                    floatFprem(false, code, stmt);
                    break;
                case FLOAT_NEG:
                    floatNegate(false, code, stmt);
                    break;
                case LONG_ADD:
                    intAdd(true, code, stmt);
                    break;
                case LONG_SUB:
                    intSub(true, code, stmt);
                    break;
                case LONG_MUL:
                    intMul(true, code, stmt);
                    break;
                case LONG_DIV:
                    intDivMod(true, "rax", code, stmt);
                    break;
                case LONG_MOD:
                    intDivMod(true, "rdx", code, stmt);
                    break;
                case LONG_NEG:
                    intUnary(true, "neg", code, stmt);
                    break;
                case LONG_CPL:
                    intUnary(true, "not", code, stmt);
                    break;
                case INT_ADD:
                    intAdd(false, code, stmt);
                    break;
                case INT_SUB:
                    intSub(false, code, stmt);
                    break;
                case INT_MUL:
                    intMul(false, code, stmt);
                    break;
                case INT_DIV:
                    intDivMod(false, "eax", code, stmt);
                    break;
                case INT_MOD:
                    intDivMod(false, "edx", code, stmt);
                    break;
                case INT_NEG:
                    intUnary(false, "neg", code, stmt);
                    break;
                case INT_CPL:
                    intUnary(false, "not", code, stmt);
                    break;
                case POP_PARAM_FLOAT: {
                    final int bs = stmt.getDataSize() / 8;
                    String dst;
                    if (floatParam < 8) {
                        dst = String.format("[rbp - %d]", (stackOffset = roundToNextDivisible(stackOffset, bs)));
                        code.add("    " + (bs == 4 ? "movss" : "movsd") + " " + dst + ", " + getFloatRegParam(floatParam));
                    } else {
                        // Leave the data on the stack for now
                        dst = String.format("[rbp + %d]", 16 + 8 * (floatParam - 8));
                    }

                    dataMapping.put(stmt.dst, dst);
                    ++floatParam;
                    break;
                }
                case POP_PARAM_INT: {
                    final int bs = stmt.getDataSize() / 8;
                    String dst;
                    if (intParam < 6) {
                        dst = String.format("[rbp - %d]", (stackOffset = roundToNextDivisible(stackOffset, bs)));
                        code.add("    mov " + dst + ", " + getIntRegParam(intParam, bs));
                    } else {
                        // Leave the data on the stack for now
                        dst = String.format("[rbp + %d]", 16 + 8 * (intParam - 6));
                    }

                    dataMapping.put(stmt.dst, dst);
                    ++intParam;
                    break;
                }
                case INT_LT:
                    intCmp("setl", code, stmt);
                    break;
                case INT_GT:
                    intCmp("setg", code, stmt);
                    break;
                case INT_LE:
                    intCmp("setle", code, stmt);
                    break;
                case INT_GE:
                    intCmp("setge", code, stmt);
                    break;
                case INT_EQ:
                    intCmp("sete", code, stmt);
                    break;
                case INT_NE:
                    intCmp("setne", code, stmt);
                    break;
                case LONG_CMP: {
                    code.add("    xor ecx, ecx");
                    code.add("    mov rax, " + getNumber(stmt.lhs));
                    code.add("    cmp rax, " + getNumber(stmt.rhs));
                    code.add("    setg cl");
                    code.add("    mov eax, -1");
                    code.add("    cmovge eax, ecx");

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                }
                case FLOAT_CMP:
                    floatCmp(false, code, stmt);
                    break;
                case DOUBLE_CMP:
                    floatCmp(true, code, stmt);
                    break;
                case LOAD_TRUE:
                    loadBoolean(true, code, stmt);
                    break;
                case LOAD_FALSE:
                    loadBoolean(false, code, stmt);
                    break;
                case LOAD_NUMERAL:
                case STORE_VAR: {
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);
                    code.add("    xor rax, rax");
                    code.add("    mov " + tmp + ", " + getNumber(stmt.lhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", " + tmp);
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += bs));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", " + tmp);
                    }
                    break;
                }
                case REFER_VAR:
                    code.add("    lea rax, " + getNumber(stmt.lhs));

                    // Pointers are 8 bytes
                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", rax");
                    } else {
                        final String loc = String.format("[rbp - %d]", (stackOffset += 8));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", rax");
                    }
                    break;
                case GOTO:
                    code.add("    jmp .L" + ((Label) stmt.dst).getAddress());
                    break;
                case JUMP_IF_TRUE:
                    // int is 4 bytes
                    code.add("    cmp " + toWordSizeString(4) + " " + getNumber(stmt.lhs) + ", 0");
                    code.add("    jne .L" + ((Label) stmt.dst).getAddress());
                    break;
                case JUMP_IF_FALSE:
                    // int is 4 bytes
                    code.add("    cmp " + toWordSizeString(4) + " " + getNumber(stmt.lhs) + ", 0");
                    code.add("    je .L" + ((Label) stmt.dst).getAddress());
                    break;
                case RETURN_FLOAT:
                    code.add("    " + (stmt.getDataSize() / 8 == 4 ? "movss" : "movsd") + " xmm0, " + getNumber(stmt.dst));
                    generateFuncEpilogue(code);
                    break;
                case RETURN_INT: {
                    final int bs = stmt.getDataSize() / 8;
                    final String src = getNumber(stmt.dst);
                    switch (bs) {
                        case 1:
                            code.add("    movsx eax, " + toWordSizeString(bs) + " " + src);
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
                            throw new AssertionError("Unknown data size " + bs);
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

    private String getNumber(final Value v) {
        if (v.isNumeric()) {
            final Fixnum num = (Fixnum) v;
            if (num.isInt) {
                return num.value;
            }

            // It is float, which needs to be read from data section
            final int bs = num.size / 8;
            if (sectData.containsKey(num)) {
                return "[rel " + sectData.get(num).label + "]";
            }
            final String label = "_K" + sectData.size();
            sectData.put(num, new DataValue(label, toDataSizeString(bs) + " " + num.value));
            // Explicit relative addressing!
            return "[rel " + label + "]";
        }
        return dataMapping.get(v);
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

    private void intUnary(boolean quad, String op, List<String> code, Statement stmt) {
        final int bs = quad ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
        code.add("    " + op + " " + accum);

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + accum);
        } else {
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void intAdd(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
        code.add("    add " + accum + ", " + getNumber(stmt.rhs));

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + accum);
        } else {
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void intSub(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
        code.add("    sub " + accum + ", " + getNumber(stmt.rhs));

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + accum);
        } else {
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void intMul(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
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
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + accum);
        }
    }

    private void intDivMod(boolean eightBytes, String resultReg, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);

        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
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
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + resultReg);
        }
    }

    private void intCmp(String cmpInstr, List<String> code, Statement stmt) {
        code.add("    xor ecx, ecx");
        code.add("    mov eax, " + getNumber(stmt.lhs));
        code.add("    cmp eax, " + getNumber(stmt.rhs));
        code.add("    " + cmpInstr + " cl");

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", ecx");
        } else {
            // int is 4 bytes
            final String loc = String.format("[rbp - %d]", (stackOffset += 4));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", ecx");
        }
    }

    private void loadBoolean(boolean value, List<String> code, Statement stmt) {
        final String k = value ? "1" : "0";
        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", " + k);
        } else {
            // bool is 1 byte
            final String loc = String.format("[rbp - %d]", (stackOffset += 1));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", " + k);
        }
    }

    private void int2Float(boolean quadIn, boolean quadOut, List<String> code, Statement stmt) {
        final String input = quadIn ? "rdi" : "edi";
        final String convOp = quadOut ? "cvtsi2sd" : "cvtsi2ss";
        final String outOp = quadOut ? "movsd" : "movss";
        code.add("    mov " + input + ", " + getNumber(stmt.lhs));
        code.add("    " + convOp + " xmm1, " + input);

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    " + outOp + " " + dataMapping.get(stmt.dst) + ", xmm1");
        } else {
            final int bs = quadOut ? 8 : 4;
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    " + outOp + " " + loc + ", xmm1");
        }
    }

    private void floatSSEMath(boolean quad, String opPrefix, List<String> code, Statement stmt) {
        final String mov = quad ? "movsd": "movss";
        final String add = opPrefix + (quad ? "sd": "ss");
        code.add("    " + mov + " xmm0, " + getNumber(stmt.lhs));
        code.add("    " + add + " xmm0, " + getNumber(stmt.rhs));

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    " + mov + " " + dataMapping.get(stmt.dst) + ", xmm0");
        } else {
            final int bs = quad ? 8 : 4;
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    " + mov + " " + loc + ", xmm0");
        }
    }

    private void floatFprem(boolean quad, List<String> code, Statement stmt) {
        final int bs = quad ? 8 : 4;
        final String sz = toWordSizeString(bs);
        code.add("    ;; ST(1) <- rhs");
        code.add("    fld " + sz + " " + getNumber(stmt.rhs));
        code.add("    ;; ST(0) <- lhs");
        code.add("    fld " + sz + " " + getNumber(stmt.lhs));
        code.add("    ;; ST(0) <- ST(0) % ST(1)");
        code.add("    fprem");

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    fstp " + sz + " " + dataMapping.get(stmt.dst));
        } else {
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    fstp " + sz + " " + loc);
        }
    }

    private void floatCmp(boolean quad, List<String> code, Statement stmt) {
        final String mov = quad ? "movsd" : "movss";
        final String cmp = quad ? "ucomisd" : "ucomiss";
        code.add("    xor ecx, ecx");
        code.add("    " + mov + " xmm0, " + getNumber(stmt.lhs));
        code.add("    " + mov + " xmm1, " + getNumber(stmt.rhs));
        code.add("    " + cmp + " xmm0, xmm1");
        code.add("    seta cl");
        code.add("    " + cmp + " xmm1, xmm0");
        code.add("    mov eax, -1");
        code.add("    cmovbe eax, ecx");

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
        } else {
            // int is 4 bytes
            final String loc = String.format("[rbp - %d]", (stackOffset += 4));
            dataMapping.put(stmt.dst, loc);
            code.add("    mov " + loc + ", eax");
        }
    }

    private void floatNegate(boolean quad, List<String> code, Statement stmt) {
        final String mov = quad ? "movsd" : "movss";
        final String neg = quad ? "xorpd" : "xorps";
        final String dat = quad ? "CC1" : "CC0";
        code.add("    " + mov + " xmm0, " + getNumber(stmt.lhs));
        code.add("    " + neg + " xmm0, [rel " + dat + "]");

        if (dataMapping.containsKey(stmt.dst)) {
            code.add("    " + mov + " " + dataMapping.get(stmt.dst) + ", xmm0");
        } else {
            final int bs = quad ? 8 : 4;
            final String loc = String.format("[rbp - %d]", (stackOffset += bs));
            dataMapping.put(stmt.dst, loc);
            code.add("    " + mov + " " + loc + ", xmm0");
        }
    }
}
