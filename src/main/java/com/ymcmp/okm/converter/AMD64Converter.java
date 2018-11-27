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

    private static final String SECTION_DATA_HEADER =
            "    section .data\n" +
            "    align 16\n" +
            "CC0 dd 2147483648,0,0,0\n" +
            "CC1 dd 0,-2147483648,0,0";

    private static final String SECTION_BSS_HEADER =
            "    section .bss";

    private static final String SECTION_TEXT_HEADER =
            "    section .text";

    private final Map<Fixnum, DataValue> sectData = new HashMap<>();
    private final Map<String, String> sectBss = new HashMap<>();
    private final List<String> sectText = new ArrayList<>();

    private final List<String> funcPrologue = new ArrayList<>();
    private final List<String> funcEpilogue = new ArrayList<>();
    private final HashMap<Value, String> dataMapping = new HashMap<>();
    private int stackOffset;

    @Override
    public void reset() {
        sectData.clear();
        sectBss.clear();
        sectText.clear();
    }

    @Override
    public String getResult() {
        return Stream.concat(Stream.concat(
                Stream.concat(Stream.of(SECTION_DATA_HEADER), sectData.values().stream().map(DataValue::output)),
                Stream.concat(Stream.of(SECTION_BSS_HEADER), sectBss.values().stream().map(e -> e.startsWith("??") ? "extern " + e.substring(2) : e))),
                Stream.concat(Stream.of(SECTION_TEXT_HEADER), sectText.stream()))
                .collect(Collectors.joining("\n\n"));
    }

    private static String mangleName(final String name) {
        final String subst = name.substring(1);
        return "_F" + (subst.indexOf(':') + 1) + "_" + subst.replace(":", "_");
    }

    @Override
    public void convert(final String name, final List<Statement> body) {
        // Reset necessary fields
        funcPrologue.clear();
        funcEpilogue.clear();
        dataMapping.clear();
        stackOffset = 0;

        if (name.equals("@init")) {
            // This is the equivalent of the int main function in C
            funcPrologue.add("    global _main");
            funcPrologue.add("_main:");

            // Explicitly clear out eax register at the end
            funcEpilogue.add("    xor eax, eax");
        } else {
            funcPrologue.add(mangleName(name) + ":");
        }
        funcPrologue.add("    ;;@ prologue");
        funcPrologue.add("    push rbp");
        funcPrologue.add("    mov rbp, rsp");

        final ArrayList<String> code = new ArrayList<>();

        int popIntParam = 0;
        int popFloatParam = 0;

        int pushIntParam = 0;
        int pushFloatParam = 0;

        boolean moveRSP = false;

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
                    code.add("    mov " + getOrAllocSite(4, stmt.dst) + ", edi");
                    break;
                case CONV_INT_LONG:
                    code.add("    movsxd rdi, " + getNumber(stmt.lhs));
                    code.add("    mov " + getOrAllocSite(8, stmt.dst) + ", rdi");
                    break;
                case CONV_INT_BYTE:
                    code.add("    mov edi, " + getNumber(stmt.lhs));
                    code.add("    mov " + getOrAllocSite(1, stmt.dst) + ", dil");
                    break;
                case CONV_INT_SHORT:
                    code.add("    mov edi, " + getNumber(stmt.lhs));
                    code.add("    mov " + getOrAllocSite(2, stmt.dst) + ", di");
                    break;
                case CONV_LONG_INT:
                    code.add("    mov rdi, " + getNumber(stmt.lhs));
                    code.add("    mov " + getOrAllocSite(4, stmt.dst) + ", edi");
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
                    code.add("    movsd " + getOrAllocSite(8, stmt.dst) + ", xmm0");
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
                    String dst;
                    if (popFloatParam < 8) {
                        final int bs = stmt.getDataSize() / 8;
                        dst = String.format("[rbp - %d]", (stackOffset = roundToNextDivisible(stackOffset, bs)));
                        code.add("    " + (bs == 4 ? "movss" : "movsd") + " " + dst + ", " + getFloatRegParam(popFloatParam));
                    } else {
                        // Leave the data on the stack for now
                        dst = String.format("[rbp + %d]", 16 + 8 * (popFloatParam - 8));
                    }

                    dataMapping.put(stmt.dst, dst);
                    ++popFloatParam;
                    break;
                }
                case POP_PARAM_INT: {
                    final int bs = stmt.getDataSize() / 8;
                    String dst = null;
                    if (popIntParam < 6) {
                        if (bs > 8) {
                            // Data is huge, caller left the pointer to it
                            final String addr = String.format("[rbp - %d]", (stackOffset += 8));
                            code.add("    mov " + addr + ", " + getIntRegParam(popIntParam, 8));

                            // right now, addr contains the pointer to the data
                            // alloca will assign stmt.dst to a location
                            alloca(bs, stmt.dst, code);
                            code.add("    mov rax, " + addr);
                            memcpyRaxToStack(bs, code);
                        } else {
                            dst = String.format("[rbp - %d]", (stackOffset = roundToNextDivisible(stackOffset, bs)));
                            code.add("    mov " + dst + ", " + getIntRegParam(popIntParam, bs));
                        }
                    } else {
                        final String dataSrc = String.format("[rbp + %d]", 16 + 8 * (popIntParam - 6));
                        if (bs > 8) {
                            // Have to copy the data from pointer
                            code.add("    mov rax, " + dataSrc);

                            // right now, rax contains the pointer to the data
                            // alloca will assign stmt.dst to a location
                            alloca(bs, stmt.dst, code);
                            memcpyRaxToStack(bs, code);
                        } else {
                            // Leave the data on the stack for now
                            dst = dataSrc;
                        }
                    }

                    if (dst != null) {
                        dataMapping.put(stmt.dst, dst);
                    }
                    ++popIntParam;
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
                    code.add("    mov " + getOrAllocSite(1, stmt.dst) + ", al");
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
                    code.add("    mov " + tmp + ", " + getNumber(stmt.lhs));
                    code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + tmp);
                    break;
                }
                case LOAD_FUNC:
                    code.add("    lea rax, [rel " + mangleName(stmt.lhs.toString()) + "]");
                    code.add("    mov " + getOrAllocSite(8, stmt.dst) + ", rax");
                    break;
                case REFER_VAR:
                    code.add("    lea rax, " + getNumber(stmt.lhs));
                    code.add("    mov " + getOrAllocSite(8, stmt.dst) + ", rax");
                    break;
                case REFER_ATTR: {
                    final int bs = stmt.getDataSize() / 8;

                    code.add("    mov rax, " + getNumber(stmt.lhs));
                    code.add("    add rax, " + (Long.parseLong(((Fixnum) stmt.rhs).value) / 8));
                    code.add("    mov " + getOrAllocSite(8, stmt.dst) + ", rax");
                    break;
                }
                case POINTER_GET: {
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);
                    code.add("    mov rax, " + getNumber(stmt.lhs));
                    code.add("    mov " + tmp + ", [rax]");
                    code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + tmp);
                    break;
                }
                case POINTER_PUT: {
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);
                    code.add("    mov rdi, " + getNumber(stmt.dst));
                    code.add("    mov " + tmp + ", " + getNumber(stmt.lhs));
                    code.add("    mov [rdi], " + tmp);
                    break;
                }
                case GOTO:
                    code.add("    jmp .L" + ((Label) stmt.dst).getAddress());
                    break;
                case JUMP_IF_TRUE:
                    // bool is 1 byte
                    code.add("    cmp " + toWordSizeString(1) + " " + getNumber(stmt.lhs) + ", 0");
                    code.add("    jne .L" + ((Label) stmt.dst).getAddress());
                    break;
                case JUMP_IF_FALSE:
                    // bool is 1 byte
                    code.add("    cmp " + toWordSizeString(1) + " " + getNumber(stmt.lhs) + ", 0");
                    code.add("    je .L" + ((Label) stmt.dst).getAddress());
                    break;
                case RETURN_FLOAT:
                    code.add("    " + (stmt.getDataSize() / 8 == 4 ? "movss" : "movsd") + " xmm0, " + getNumber(stmt.dst));
                    generateFuncEpilogue(code);
                    code.add("    ret");
                    break;
                case RETURN_INT: {
                    final int bs = stmt.getDataSize() / 8;
                    if (bs > 8) {
                        // Returning a big struct, move the ptr to rax
                        code.add("    lea rax, " + getNumber(stmt.dst));
                    } else {
                        moveSignExtend(bs, code, getNumber(stmt.dst));
                    }
                    generateFuncEpilogue(funcEpilogue);
                    funcEpilogue.add("    ret");
                    break;
                }
                case RETURN_UNIT:
                    generateFuncEpilogue(funcEpilogue);
                    funcEpilogue.add("    ret");
                    break;
                case CALL_NATIVE:
                    // Almost like a tail call, except it doesnt need prologue or epilogue
                    // it just jumps!
                    pushIntParam = pushFloatParam = 0;
                    funcPrologue.subList(1, funcPrologue.size()).clear();
                    funcEpilogue.clear();
                    code.clear();

                    funcEpilogue.add("    ;;@ native call");
                    funcEpilogue.add("    jmp _" + stmt.dst);

                    funcPrologue.add(0, "    extern _" + stmt.dst);
                    break;
                case CALL_INT: {
                    moveRSP = true;
                    pushIntParam = pushFloatParam = 0;
                    code.add("    call " + getNumber(stmt.lhs));

                    final int bs = stmt.getDataSize() / 8;
                    if (bs > 8) {
                        alloca(bs, stmt.dst, code);
                        memcpyRaxToStack(bs, code);
                    } else {
                        // Expect return value to be in [al, rax]
                        code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + getIntRegister(bs));
                    }
                    break;
                }
                case CALL_FLOAT: {
                    moveRSP = true;
                    pushIntParam = pushFloatParam = 0;
                    code.add("    call " + getNumber(stmt.lhs));

                    // Expect return value to be in xmm0
                    final int bs = stmt.getDataSize() / 8;
                    code.add("    " + (bs == 8 ? "movsd" : "movss") + " " + getOrAllocSite(bs, stmt.dst) + ", xmm0");
                    break;
                }
                case CALL_UNIT:
                    moveRSP = true;
                    pushIntParam = pushFloatParam = 0;
                    code.add("    call " + getNumber(stmt.dst));
                    break;
                case TAILCALL:
                    pushIntParam = pushFloatParam = 0;
                    generateFuncEpilogue(funcEpilogue);
                    funcEpilogue.add("    ;;@ tailcall");
                    funcEpilogue.add("    jmp " + getNumber(stmt.dst));
                    break;
                case PUSH_PARAM_INT: {
                    final int bs = stmt.getDataSize() / 8;
                    if (pushIntParam < 6) {
                        if (bs > 8) {
                            // Data is huge, pass pointer to it (pointer is 8 bytes)
                            code.add("    lea " + getIntRegParam(pushIntParam, 8) + ", " + getNumber(stmt.dst));
                        } else {
                            // Pass via register
                            final int prefSize = Math.max(4, bs);
                            moveSignExtend(bs, code, getNumber(stmt.dst));
                            code.add("    mov " + getIntRegParam(pushIntParam, prefSize) + ", " + getIntRegister(prefSize));
                        }
                    } else {
                        final String site = String.format("[rsp + %d]", 8 * (pushIntParam - 6));
                        if (bs > 8) {
                            // Data is huge, pass pointer to it (on the stack)
                            code.add("    lea rax, " + getNumber(stmt.dst));
                            code.add("    mov " + site + ", rax");
                        } else {
                            // Pass via stack
                            code.add("    mov " + site + ", " + getNumber(stmt.dst));
                        }
                    }

                    ++pushIntParam;
                    break;
                }
                case PUSH_PARAM_FLOAT: {
                    final int bs = stmt.getDataSize() / 8;
                    if (pushFloatParam < 8) {
                        // Pass via register
                        code.add("    " + (bs == 4 ? "movss" : "movsd") + " " + getFloatRegParam(pushFloatParam) + ", " + getNumber(stmt.dst));
                    } else {
                        // Pass via stack
                        code.add("    mov [rsp + " + 8 * (pushFloatParam - 8) + "], " + getNumber(stmt.dst));
                    }

                    ++pushFloatParam;
                    break;
                }
                case ALLOC_STRUCT:
                    // Acquire a pointer to the start of the struct
                    alloca(stmt.getDataSize() / 8, stmt.dst, code);
                    break;
                case PUT_ATTR: {
                    // dst is the value being dumped into the struct
                    // lhs is the pointer of the struct
                    // rhs is the offset we are working with!
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);
                    final StringBuilder structHead = new StringBuilder(getNumber(stmt.lhs));
                    structHead
                            .insert(structHead.length() - 1, " + ")
                            .insert(structHead.length() - 1, Long.parseLong(((Fixnum) stmt.rhs).value) / 8);

                    code.add("    mov " + tmp + ", " + getNumber(stmt.dst));
                    code.add("    mov " + structHead + ", " + tmp);
                    break;
                }
                case DEREF_PUT_ATTR: {
                    // dst is the value being dumped into the struct
                    // lhs is the pointer to a struct pointer
                    // rhs is the offset we are working with!
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);

                    code.add("    mov " + tmp + ", " + getNumber(stmt.dst));
                    code.add("    mov rdi, " + getNumber(stmt.lhs));
                    code.add("    mov [rdi + " + (Long.parseLong(((Fixnum) stmt.rhs).value) / 8) + "], " + tmp);
                    break;
                }
                case GET_ATTR: {
                    // dst is the output
                    // lhs is the pointer of the struct
                    // rhs is the offset we are working with!
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);
                    final StringBuilder structHead = new StringBuilder(getNumber(stmt.lhs));
                    structHead
                            .insert(structHead.length() - 1, " + ")
                            .insert(structHead.length() - 1, Long.parseLong(((Fixnum) stmt.rhs).value) / 8);

                    code.add("    mov " + tmp + ", " + structHead);
                    code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + tmp);
                    break;
                }
                case DEREF_GET_ATTR: {
                    // dst is the output
                    // lhs is the pointer to a struct pointer
                    // rhs is the offset we are working with!
                    final int bs = stmt.getDataSize() / 8;
                    final String tmp = getIntRegister(bs);

                    code.add("    mov rax, " + getNumber(stmt.lhs));
                    code.add("    mov " + tmp + ", [rax + " + (Long.parseLong(((Fixnum) stmt.rhs).value) / 8) + "]");
                    code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + tmp);
                    break;
                }
                default:
                    code.add(stmt.toString());
                    break;
            }
        }

        if (moveRSP) {
            final int relocate = roundToNextDivisible(stackOffset, 16);
            funcPrologue.add("    sub rsp, " + relocate);
            funcEpilogue.add(0, "    add rsp, " + relocate);
        } else if (stackOffset > 128) {
            // Stack is more than red-zone, need relocate rsp
            final int relocate = stackOffset - 128;
            funcPrologue.add("    sub rsp, " + relocate);
            funcEpilogue.add(0, "    add rsp, " + relocate);
        }
        sectText.add(Stream.concat(Stream.concat(funcPrologue.stream(), code.stream()), funcEpilogue.stream())
                .collect(Collectors.joining("\n")));
    }

    private static void generateFuncEpilogue(final List<String> code) {
        code.add("    ;;@ epilogue");
        code.add("    pop rbp");
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

    private static String toBssSizeString(int size) {
        switch (size) {
            case 1: return "resb";
            case 2: return "resw";
            case 4: return "resd";
            case 8: return "resq";
        }
        throw new AssertionError("Invalid bss size " + size);
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

        final String handle = v.toString();
        if (handle.startsWith("@M")) {
            final String mangled = mangleName(handle);
            if (handle.charAt(handle.length() - 1) == ':') {
                // function
                return mangled;
            }
            // global variable
            if (!sectBss.containsKey(handle)) {
                sectBss.put(handle, "??" + mangled);
            }
            return String.format("[rel %s]", mangled);
        }
        // local variable
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
        code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + accum);
    }

    private void intAdd(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
        code.add("    add " + accum + ", " + getNumber(stmt.rhs));
        code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + accum);
    }

    private void intSub(boolean eightBytes, List<String> code, Statement stmt) {
        final int bs = eightBytes ? 8 : 4;
        final String accum = getIntRegister(bs);
        code.add("    mov " + accum + ", " + getNumber(stmt.lhs));
        code.add("    sub " + accum + ", " + getNumber(stmt.rhs));
        code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + accum);
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

        code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + accum);
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

        code.add("    mov " + getOrAllocSite(bs, stmt.dst) + ", " + resultReg);
    }

    private void intCmp(String cmpInstr, List<String> code, Statement stmt) {
        code.add("    xor ecx, ecx");
        code.add("    mov eax, " + getNumber(stmt.lhs));
        code.add("    cmp eax, " + getNumber(stmt.rhs));
        code.add("    " + cmpInstr + " cl");
        code.add("    mov " + getOrAllocSite(1, stmt.dst) + ", cl");
    }

    private void loadBoolean(boolean value, List<String> code, Statement stmt) {
        code.add("    mov " + getOrAllocSite(1, stmt.dst) + ", " + (value ? "1" : "0"));
    }

    private void int2Float(boolean quadIn, boolean quadOut, List<String> code, Statement stmt) {
        final String input = quadIn ? "rdi" : "edi";
        final String convOp = quadOut ? "cvtsi2sd" : "cvtsi2ss";
        final String outOp = quadOut ? "movsd" : "movss";
        code.add("    mov " + input + ", " + getNumber(stmt.lhs));
        code.add("    " + convOp + " xmm1, " + input);
        code.add("    " + outOp + " " + getOrAllocSite(quadOut ? 8 : 4, stmt.dst) + ", xmm1");
    }

    private void floatSSEMath(boolean quad, String opPrefix, List<String> code, Statement stmt) {
        final String mov = quad ? "movsd": "movss";
        final String add = opPrefix + (quad ? "sd": "ss");
        code.add("    " + mov + " xmm0, " + getNumber(stmt.lhs));
        code.add("    " + add + " xmm0, " + getNumber(stmt.rhs));
        code.add("    " + mov + " " + getOrAllocSite(quad ? 8 : 4, stmt.dst) + ", xmm0");
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
        code.add("    fstp " + sz + " " + getOrAllocSite(bs, stmt.dst));
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
        code.add("    mov " + getOrAllocSite(1, stmt.dst) + ", al");
    }

    private void floatNegate(boolean quad, List<String> code, Statement stmt) {
        final String mov = quad ? "movsd" : "movss";
        final String neg = quad ? "xorpd" : "xorps";
        final String dat = quad ? "CC1" : "CC0";
        code.add("    " + mov + " xmm0, " + getNumber(stmt.lhs));
        code.add("    " + neg + " xmm0, [rel " + dat + "]");
        code.add("    " + mov + " " + getOrAllocSite(quad ? 8 : 4, stmt.dst) + ", xmm0");
    }

    private void moveSignExtend(int bs, List<String> code, String value) {
        switch (bs) {
            case 1:
                code.add("    movsx eax, " + toWordSizeString(bs) + " " + value);
                break;
            case 2:
                code.add("    mov ax, " + value);
                code.add("    cwde");
                break;
            case 4:
                code.add("    mov eax, " + value);
                break;
            case 8:
                code.add("    mov rax, " + value);
                break;
            default:
                throw new AssertionError("Unknown data size " + bs);
        }
    }

    private void alloca(int bytes, Value dst, List<String> code) {
        // Acquire a pointer to block of data
        code.add("    lea rdi, [rbp - " + stackOffset + "]");
        stackOffset += bytes;

        code.add("    mov " + getOrAllocSite(8, dst) + ", rdi");
    }

    private String getOrAllocSite(int bs, Value site) {
        if (dataMapping.containsKey(site)) {
            return dataMapping.get(site);
        }

        final String handle = site.toString();
        final String loc;
        if (handle.startsWith("@M")) {
            // global variable
            final String mangled = mangleName(handle);
            loc = String.format("[rel %s]", mangled);
            if (!sectBss.containsKey(handle) || sectBss.get(handle).startsWith("??")) {
                sectBss.put(handle, mangled + ": " + toBssSizeString(bs) + " 1");
            }
        } else {
            // local variable
            loc = String.format("[rbp - %d]", (stackOffset += bs));
        }
        dataMapping.put(site, loc);
        return loc;
    }

    private void memcpyRaxToStack(final int bs, List<String> code) {
        // Copy as 8 bytes first
        int iter = 0;
        for ( ; iter <= bs - 8; iter += 8) {
            code.add("    mov rsi, [rax + " + iter + "]");
            code.add("    mov [rbp - " + stackOffset + " + " + iter + "], rsi");
        }
        // Copy as 4 bytes
        for ( ; iter <= bs - 4; iter += 4) {
            code.add("    mov esi, [rax + " + iter + "]");
            code.add("    mov [rbp - " + stackOffset + " + " + iter + "], esi");
        }
        // Copy as 2 bytes
        for ( ; iter <= bs - 2; iter += 2) {
            code.add("    mov si, [rax + " + iter + "]");
            code.add("    mov [rbp - " + stackOffset + " + " + iter + "], si");
        }
        // Copy the remaining bytes (if bs was not multiple of 8)
        for ( ; iter < bs; ++iter) {
            code.add("    mov sil, [rax + " + iter + "]");
            code.add("    mov [rbp - " + stackOffset + " + " + iter + "], sil");
        }
    }
}

final class DataValue {

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