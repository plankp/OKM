package com.ymcmp.okm.converter;

import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import java.util.stream.Collectors;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public class X86Converter implements Converter {

    @Override
    public String convert(final String name, final List<Statement> body) {
        final ArrayList<String> code = new ArrayList<>();
        code.add(name + ":");
        code.add("    push ebp        ; save old call frame");
        code.add("    mov ebp, esp    ; initialize new call frame");

        final HashMap<Value, String> dataMapping = new HashMap<>();
        int paramOffset = 4;
        int stackOffset = 0;

        for (int i = 0; i < body.size(); ++i) {
            final Statement stmt = body.get(i);
            code.add("  .L" + i + ":");

            switch (stmt.op) {
                case INT_ADD:
                    // ADD <mem>, <mem> is not allowed. Dump #lhs to eax then ADD eax, #rhs
                    code.add("    mov eax, " + dataMapping.get(stmt.lhs));
                    code.add("    add eax, " + dataMapping.get(stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [ebp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_SUB:
                    // SUB <mem>, <mem> is not allowed. Dump #lhs to eax then SUB eax, #rhs
                    code.add("    mov eax, " + dataMapping.get(stmt.lhs));
                    code.add("    sub eax, " + dataMapping.get(stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [ebp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_MUL:
                    // IMUL <mem>, <mem> is not allowed. Dump #lhs to eax then IMUL #rhs
                    code.add("    mov eax, " + dataMapping.get(stmt.lhs));
                    code.add("    imul " + dataMapping.get(stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [ebp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_DIV:
                    // IDIV <mem>, <mem> is not allowed. Dump #lhs to eax then CDQ IDIV #rhs
                    code.add("    mov eax, " + dataMapping.get(stmt.lhs));
                    code.add("    cdq");
                    code.add("    idiv " + dataMapping.get(stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", eax");
                    } else {
                        // eax is 32 bits, or 4 bytes
                        final String loc = String.format("%s [ebp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", eax");
                    }
                    break;
                case INT_MOD:
                    // Same as INT_DIV, except for we care about edx not eax for output
                    code.add("    mov eax, " + dataMapping.get(stmt.lhs));
                    code.add("    cdq");
                    code.add("    idiv " + dataMapping.get(stmt.rhs));

                    if (dataMapping.containsKey(stmt.dst)) {
                        code.add("    mov " + dataMapping.get(stmt.dst) + ", edx");
                    } else {
                        // edx is 32 bits, or 4 bytes
                        final String loc = String.format("%s [ebp - %d]", toWordSizeString(4), (stackOffset += 4));
                        dataMapping.put(stmt.dst, loc);
                        code.add("    mov " + loc + ", edx");
                    }
                    break;
                case POP_PARAM:
                    final int bs = stmt.getDataSize() / 8;
                    final String loc = String.format("%s [ebp + %d]", toWordSizeString(bs), (paramOffset += bs));
                    code.add("    ;; " + stmt.dst + " stored as " + loc);
                    dataMapping.put(stmt.dst, loc);
                    break;
                case RETURN_VALUE:
                    code.add("    mov eax, " + dataMapping.get(stmt.dst));
                    // FALLTHROUGH
                case RETURN_UNIT:
                    code.add("    ;; restore old call frame");
                    code.add("    mov esp, ebp");
                    code.add("    pop ebp");
                    code.add("    ret");
                    break;
                default:
                    code.add(stmt.toString());
                    break;
            }
        }

        // Stacks are 16 bytes aligned
        // XXX: DO not merge k / 16 * 16 together! the int cast makes this not possible
        final int stackShift = ((int) ((stackOffset + 15) / 16)) * 16;
        if (stackShift > 0) {
            code.add(3, "    ;; allocate space for locals / temporaries");
            code.add(4, "    sub esp, " + stackShift);
        }

        return code.stream().collect(Collectors.joining("\n"));
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
}
