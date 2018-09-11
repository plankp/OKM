package com.ymcmp.okm;

import java.io.IOException;

import java.nio.file.Path;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;

import java.util.stream.Collectors;

import java.util.logging.Logger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import org.antlr.v4.runtime.tree.ParseTree;

import com.ymcmp.okm.opt.*;
import com.ymcmp.okm.tac.*;
import com.ymcmp.okm.type.*;
import com.ymcmp.okm.except.*;

import com.ymcmp.okm.grammar.OkmLexer;
import com.ymcmp.okm.grammar.OkmParser;
import com.ymcmp.okm.grammar.OkmParser.*;
import com.ymcmp.okm.grammar.OkmBaseVisitor;

public class LocalVisitor extends OkmBaseVisitor<Object> {

    public static final Logger LOGGER = Logger.getLogger(LocalVisitor.class.getName());

    private static final Map<String, UnaryOperator> UNI_OP_MAPPING = new HashMap<>();
    private static final Map<String, BinaryOperator> BIN_OP_MAPPING = new HashMap<>();

    // Only lowercase chars
    private static final Map<Character, Tuple<String, Integer>> NUM_LIT_INFO = new HashMap<>();

    private static final List<Pass> OPT_PASSES = new ArrayList<>();

    private static final UnaryType TYPE_BYTE = UnaryType.getType("byte");
    private static final UnaryType TYPE_SHORT = UnaryType.getType("short");
    private static final UnaryType TYPE_INT = UnaryType.getType("int");
    private static final UnaryType TYPE_LONG = UnaryType.getType("long");
    private static final UnaryType TYPE_FLOAT = UnaryType.getType("float");
    private static final UnaryType TYPE_DOUBLE = UnaryType.getType("double");

    static {
        UNI_OP_MAPPING.put("+", UnaryOperator.ADD);
        UNI_OP_MAPPING.put("-", UnaryOperator.SUB);
        UNI_OP_MAPPING.put("~", UnaryOperator.TILDA);

        BIN_OP_MAPPING.put("+", BinaryOperator.ADD);
        BIN_OP_MAPPING.put("-", BinaryOperator.SUB);
        BIN_OP_MAPPING.put("*", BinaryOperator.MUL);
        BIN_OP_MAPPING.put("/", BinaryOperator.DIV);
        BIN_OP_MAPPING.put("%", BinaryOperator.MOD);
        BIN_OP_MAPPING.put("<", BinaryOperator.LESSER_THAN);
        BIN_OP_MAPPING.put(">", BinaryOperator.GREATER_THAN);
        BIN_OP_MAPPING.put("<=", BinaryOperator.LESSER_EQUALS);
        BIN_OP_MAPPING.put("=>", BinaryOperator.GREATER_EQUALS);
        BIN_OP_MAPPING.put("==", BinaryOperator.EQUALS);
        BIN_OP_MAPPING.put("!=", BinaryOperator.NOT_EQUALS);

        NUM_LIT_INFO.put('b', new Tuple<>("byte", Byte.SIZE));
        NUM_LIT_INFO.put('s', new Tuple<>("short", Short.SIZE));
        NUM_LIT_INFO.put('i', new Tuple<>("int", Integer.SIZE));
        NUM_LIT_INFO.put('l', new Tuple<>("long", Long.SIZE));
        NUM_LIT_INFO.put('f', new Tuple<>("float", Float.SIZE));
        NUM_LIT_INFO.put('d', new Tuple<>("double", Double.SIZE));

        OPT_PASSES.add(new ReduceMovePass());
        OPT_PASSES.add(new ConstantFoldPass());
        OPT_PASSES.add(new TailCallPass());
        OPT_PASSES.add(new EliminateDeadCodePass());
    }

    private final EntryNamingStrategy NAMING_STRAT = new EntryNamingStrategy() {

        private final Map<Path, Integer> storage = new HashMap<>();

        @Override
        public String name(final Module.Entry entry, final String name) {
            Integer i = storage.get(entry.source);
            if (i == null) {
                storage.put(entry.source, i = storage.size());
            }
            return "@M" + i + "_" + name;
        }
    };

    private final List<Path> SEARCH_PATH;

    private final Map<Path, Module> LOADED_MODULES = new HashMap<>();
    private final ArrayDeque<Value> VALUE_STACK = new ArrayDeque<>();

    private final Map<String, List<Statement>> RESULT = new LinkedHashMap<>();
    private final List<String> MODULE_INITS = new ArrayList<>();
    private final List<Statement> PRE_INIT_STMTS = new ArrayList<>();

    private Path currentFile;
    private Module currentModule;
    private Visibility currentVisibility;

    private Scope currentScope;
    private Type conformingType;
    private List<Statement> funcStmts;

    private StructType currentStruct;

    private List<Tuple<Scope, FunctionDeclContext>> pendingFunctions;

    public LocalVisitor() {
        this(Arrays.asList());
    }

    public LocalVisitor(final List<Path> moduleSearchPath) {
        this.SEARCH_PATH = moduleSearchPath == null ? Arrays.asList() : moduleSearchPath;
    }

    public Map<String, List<Statement>> compile(final List<Path> ps) {
        RESULT.clear();
        ps.forEach(this::processModule);

        // define a function called unit @init() { }
        // which performs initializations
        final List<Statement> initializer = new ArrayList<>();
        // Perform pre-initialization (such as setting up enums)
        initializer.addAll(PRE_INIT_STMTS);
        // Initializes all included modules
        for (final String func : MODULE_INITS) {
            initializer.add(new Statement(Operation.CALL, Register.makeNamed(func), Register.makeTemporary()));
        }
        // Use RETURN_UNIT 
        initializer.add(new Statement(Operation.RETURN_UNIT));
        RESULT.put("@init", initializer);

        Register.resetCounter();

        return Collections.unmodifiableMap(RESULT);
    }

    public Module processModule(final Path p) {
        // Make sure path is absolute and normalized
        final Path path = p.normalize().toAbsolutePath();
        LOGGER.info("Loading file: " + path);
        if (!LOADED_MODULES.containsKey(path)) {
            LOGGER.info("Processing file since it is new");
            try {
                final OkmLexer lexer = new OkmLexer(CharStreams.fromPath(path));
                final CommonTokenStream tokens = new CommonTokenStream(lexer);
                final OkmParser parser = new OkmParser(tokens);

                // Save and update state
                final Path oldFile = currentFile;
                currentFile = path;
                final Module oldModule = currentModule;
                LOADED_MODULES.put(path, currentModule = new Module());

                visit(parser.program());
                LOGGER.info("Done processing, caching result");

                // Restore state
                currentFile = oldFile;
                currentModule = oldModule;
            } catch (IOException ex) {
                throw new CannotLoadFileException(path, ex);
            }
        }
        return LOADED_MODULES.get(path);
    }

    @Override
    public Object visitProgram(final ProgramContext ctx) {
        // Save
        final List<Tuple<Scope, FunctionDeclContext>> oldPendingFunctions = pendingFunctions;
        pendingFunctions = new ArrayList<>();

        visitChildren(ctx);

        // Process functions after imported symbols are processed
        for (final Tuple<Scope, FunctionDeclContext> funcInfo : pendingFunctions) {
            final FunctionDeclContext fctx = funcInfo.getB();

            // This scope already contains the local parameters
            currentScope = funcInfo.getA();
            final String mangledName = currentScope.getProcessedName(NAMING_STRAT, currentScope.functionName);

            if (RESULT.containsKey(mangledName)) {
                throw new DuplicateSymbolException(currentScope.functionName);
            }

            // Define the return type of the function
            conformingType = visitType(fctx.ret);

            // Allocate function statement buffer
            funcStmts = new ArrayList<>();

            // Callee retrieves arguments
            for (final String param : currentScope.getCurrentLocals()) {
                final Register slot = Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, param));
                funcStmts.add(new Statement(Operation.POP_PARAM, slot));
            }

            // Process function body here
            LOGGER.info("Process function body of " + currentScope.functionName);
            if (fctx.bodyBlock == null) {
                // The expression is returned as if it was in a
                // block with a single return statement
                processReturn((Type) visit(fctx.bodyExpr));
            } else {
                // Let the block visitor handle the function body
                visitBlock(fctx.bodyBlock);
            }

            // Functions *must* end with either a branching instruction
            // next if block will be true If funcStmts does not end with a branch op
            if (funcStmts.isEmpty() ? true : !funcStmts.get(funcStmts.size() - 1).op.branches()) {
                // If the return type is unit, we will add it
                if (conformingType.isSameType(UnaryType.getType("unit"))) {
                    funcStmts.add(new Statement(Operation.RETURN_UNIT));
                } else {
                    throw new RuntimeException("Function " + currentScope.functionName + " does not return!");
                }
            }

            // Perform optimization only if program is *correct*
            final EliminateNopPass eliminateNop = new EliminateNopPass();
            for (int i = 0; i < 2; ++i) {
                for (final Pass pass : OPT_PASSES) {
                    pass.process(mangledName, funcStmts);
                    pass.reset();
                    eliminateNop.process(mangledName, funcStmts);
                    eliminateNop.reset();
                }
            }

            // if function has the same name as the module and takes no parameters
            final String synthName = currentScope.functionName.substring(0, currentScope.functionName.length() - 1) + ".okm";
            if (currentFile.endsWith(synthName)) {
                MODULE_INITS.add(mangledName);
            }

            RESULT.put(mangledName, funcStmts);

            // VALUE_STACK should be empty, but in case it isn't
            // functions have separate stack-frames. Clear them
            // and let gc cleanup!
            VALUE_STACK.clear();

            // Reset counter
            Register.resetCounter();

            // Make currentScope null
            currentScope = null;
        }
        pendingFunctions = oldPendingFunctions;
        return null;
    }

    @Override
    public Object visitDecls(final DeclsContext ctx) {
        if (ctx.empty != null) {
            // It was a semicolon used to separate declarations.
            // Does not do anything; skip it
            return null;
        }

        // Save
        final Visibility oldVisibility = currentVisibility;

        if (ctx.accMod == null) {
            currentVisibility = Visibility.PRIVATE;
        } else {
            currentVisibility = Visibility.valueOf(ctx.accMod.getText().toUpperCase());
        }

        visit(ctx.getChild(ctx.getChildCount() - 1));

        currentVisibility = oldVisibility;
        return null;
    }

    @Override
    public Module visitImportPath(final ImportPathContext ctx) {
        Path file = currentFile.getParent();

        int trySearchPathIdx = -1;
        // Unshift directories
        for (int i = 0; i < ctx.unshift.size(); ++i) {
            if ((file = file.getParent()) == null) {
                final int idx = ++trySearchPathIdx;
                if (idx < SEARCH_PATH.size()) {
                    file = SEARCH_PATH.get(idx);
                    i = -1; // reset loop state
                    continue;
                }
                throw new CannotLoadFileException(null);
            }
        }

        // Synthesize the file
        final StringBuilder filePath = new StringBuilder();
        filePath.append('.');
        for (int i = ctx.unshift.size(); i < ctx.getChildCount(); i += 2) {
            filePath.append('/').append(ctx.getChild(i).getText());
        }
        filePath.append(".okm");

        final Path newPath = file.resolve(filePath.toString()).normalize();
        final Module imported = processModule(newPath);

        final Module temporary = new Module();

        // Add a list of accessible symbols into the temporary module
        for (final Map.Entry<String, Module.Entry> entry : imported.entrySet()) {
            final Module.Entry value = entry.getValue();
            switch (value.visibility) {
                case PUBLIC:
                    // always keep
                    break;
                case PROTECTED:
                    // keep if same or subdirectory
                    if (currentFile.startsWith(newPath.getParent())) break;
                    continue;
                case INTERNAL:
                    // keep if same directory
                    if (currentFile.getParent().equals(newPath.getParent())) break;
                    continue;
                case PRIVATE:
                    // what is the point? discard symbol
                    continue;
                default:
                    throw new AssertionError("Unhandled visibility of " + entry.getValue() + " when importing");
            }

            // The imported symbols take the new declared visibility
            temporary.put(entry.getKey(), value.changeVisibility(currentVisibility));
        }

        return temporary;
    }

    @Override
    public String visitSymVariable(SymVariableContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitSymFunction(SymFunctionContext ctx) {
        final ArrayList<String> list = new ArrayList<>();
        for (int i = 3; i < ctx.getChildCount() - 1; i += 2) {
            list.add(ctx.getChild(i).getText());
        }
        return Module.makeFuncName(ctx.getChild(1).getText(), list.toArray(new String[0]));
    }

    @Override
    public List<String> visitImportList(ImportListContext ctx) {
        final List<String> ret = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i += 2) {
            ret.add((String) visit(ctx.getChild(i)));
        }
        return ret;
    }

    @Override
    public List<String> visitImportSymb(ImportSymbContext ctx) {
        return visitImportList(ctx.syms);
    }

    @Override
    public Object visitImportDecl(ImportDeclContext ctx) {
        final Module temporary = visitImportPath(ctx.path);
        if (ctx.sym == null) {
            // user does not specify which symbols to include, import all
            LOGGER.info("Import all to " + currentFile + " as " + currentVisibility + " " + temporary);
            currentModule.putAll(temporary);
        } else {
            final List<String> list = visitImportSymb(ctx.sym);
            for (final String sym : list) {
                final Module.Entry ent = temporary.get(sym);
                if (ent == null) {
                    // Only possiblity is if symbol does not exist.
                    throw new UndefinedSymbolException(sym);
                }
                LOGGER.info("Import to " + currentFile + " as " + currentVisibility + " symbol " + sym);
                currentModule.put(sym, ent);
            }
        }
        return null;
    }

    @Override
    public Type visitType(TypeContext ctx) {
        final String name = ctx.getText();
        final Module.Entry ent = currentModule.get(name);
        if (ent.isType) {
            final Type t = ent.type;
            if (t instanceof EnumType) {
                return ((EnumType) t).createCorrespondingKey();
            }
            return t;
        }
        throw new UndefinedOperationException(name + " does not name a type");
    }

    @Override
    public List<Tuple<String, Type>> visitParameter(ParameterContext ctx) {
        final List<Tuple<String, Type>> list = new ArrayList<>();
        final Type type = visitType(ctx.t);
        for (int i = 0; i < ctx.getChildCount() - 2; i += 2) {
            list.add(new Tuple<>(ctx.getChild(i).getText(), type instanceof StructType ? ((StructType) type).allocate() : type));
        }
        return list;
    }

    @Override
    public List<Tuple<String, Type>> visitParamList(ParamListContext ctx) {
        final List<Tuple<String, Type>> list = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i += 2) {
            list.addAll((List<Tuple<String, Type>>) visit(ctx.getChild(i)));
        }
        return list;
    }

    @Override
    public Object visitFunctionDecl(FunctionDeclContext ctx) {
        final String base = ctx.base.getText();
        final List<Tuple<String, Type>> params = ctx.params == null ? Collections.EMPTY_LIST : visitParamList(ctx.params);

        Type ret = visitType(ctx.ret);
        if (ret instanceof StructType) {
            ret = ((StructType) ret).allocate();
        }

        final String name = Module.makeFuncName(base, params.stream().map(Tuple::getA).toArray(String[]::new));
        final Type[] paramType = params.stream().map(Tuple::getB).toArray(Type[]::new);
        LOGGER.info("Declare " + currentVisibility + " function " + name);
        currentModule.put(name, Module.Entry.newVariable(currentVisibility, new FuncType(ret, paramType), currentFile));

        // Construct the function scope since all the required info is already present
        final Scope funcBodyScope = new Scope(name, currentModule);
        funcBodyScope.shift();

        // Add function parameters info scope
        for (final Tuple<String, Type> param : params) {
            funcBodyScope.put(param.getA(), param.getB());
        }

        // Process function body later
        pendingFunctions.add(new Tuple<>(funcBodyScope, ctx));
        return null;
    }

    @Override
    public Object visitStmts(StmtsContext ctx) {
        if (ctx.ignore == null) {
            visit(ctx.getChild(0));
            if (ctx.assign != null) {
                // assignStmt pushes a value onto the stack.
                // that value is needed as an expression,
                // but as a statement, it causes problems.
                // remove it here
                VALUE_STACK.pop();
            }
        }
        return null;
    }

    @Override
    public Object visitBlock(BlockContext ctx) {
        // Blocks create a new scope depth
        currentScope.shift();
        for (final ParseTree stmt : ctx.body) {
            visit(stmt);
        }
        currentScope.unshift();
        return null;
    }

    @Override
    public Object visitVariableDecl(VariableDeclContext ctx) {
        final List<Tuple<String, Type>> newVars = visitParameter(ctx.p);
        for (final Tuple<String, Type> newVar : newVars) {
            if (currentScope != null) {
                // This is a local variable
                LOGGER.info("Declare local variable " + newVar.getA() + " as type " + newVar.getB());
                currentScope.put(newVar.getA(), newVar.getB());
            } else if (currentStruct != null) {
                // This is part of a struct
                LOGGER.info("Declare " + currentStruct + " field " + newVar.getA() + " as type " + newVar.getB());
                currentStruct.putField(newVar.getA(), newVar.getB());
            } else {
                // This is a module level variable
                LOGGER.info("Declare " + currentVisibility + " variable " + newVar.getA() + " as type " + newVar.getB());
                currentModule.put(newVar.getA(), Module.Entry.newVariable(currentVisibility, newVar.getB(), currentFile));
            }
        }
        return null;
    }

    @Override
    public String[] visitEnumList(EnumListContext ctx) {
        final String[] arr = new String[(ctx.getChildCount() + 1) / 2];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = ctx.getChild(i * 2).getText();
        }
        return arr;
    }

    @Override
    public Object visitEnumDecl(EnumDeclContext ctx) {
        final String name = ctx.name.getText();
        final String[] keys = ctx.list == null ? new String[0] : visitEnumList(ctx.list);
        final EnumType type = EnumType.makeEnum(name, keys);
        LOGGER.info("Declare " + currentVisibility + " " + type + " with keys " + Arrays.toString(keys));
        final Module.Entry entry = Module.Entry.newType(currentVisibility, type, currentFile);
        currentModule.put(name, entry);
        PRE_INIT_STMTS.add(new Statement(Operation.LOAD_ENUM, new EnumKeys(keys), Register.makeNamed(NAMING_STRAT.name(entry, name))));
        return null;
    }

    @Override
    public Object visitStructDecl(StructDeclContext ctx) {
        final String name = ctx.name.getText();
        final StructType type = new StructType(name);
        this.currentStruct = type;
        if (ctx.list != null) {
            visit(ctx.list);
        }
        LOGGER.info("Declare " + currentVisibility + " " + type);
        final Module.Entry entry = Module.Entry.newType(currentVisibility, type, currentFile);
        currentModule.put(name, entry);
        PRE_INIT_STMTS.add(new Statement(Operation.LOAD_STRUCT, new StructFields(), Register.makeNamed(NAMING_STRAT.name(entry, name))));
        this.currentStruct = null;
        return null;
    }

    private void processReturn(Type maybeNull) {
        final Type valueType = maybeNull == null ? UnaryType.getType("unit") : maybeNull;
        if (!valueType.canConvertTo(conformingType)) {
            throw new IncompatibleTypeException(valueType, conformingType);
        }

        final Statement stmt;
        if (maybeNull == null) {
            stmt = new Statement(Operation.RETURN_UNIT);
        } else {
            stmt = new Statement(Operation.RETURN_VALUE, VALUE_STACK.pop());
        }
        funcStmts.add(stmt);
    }

    @Override
    public Object visitReturnStmt(ReturnStmtContext ctx) {
        // return statement without value means returning unit
        processReturn(ctx.value == null ? null : (Type) visit(ctx.value));
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmtContext ctx) {
        // Unfortunately for an expression a = b = c = 10
        // the tree is built like ((a = b) = c) = 10
        ParseTree lhs = ctx.dest;
        ParseTree rhs = ctx.value;

        while (lhs instanceof ExprAssignContext) {
            final ParseTree ref = lhs.getChild(2);
            processAssign(ref, rhs);
            lhs = lhs.getChild(0);
            rhs = ref;
        }
        processAssign(lhs, rhs);
        return null;
    }

    @Override
    public Object visitInfSetStmt(InfSetStmtContext ctx) {
        final String name = ctx.name.getText();
        final Type valueType = (Type) visit(ctx.value);
        currentScope.put(name, valueType);

        funcStmts.add(new Statement(Operation.STORE_VAR, VALUE_STACK.pop(), Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, name))));

        LOGGER.info("Declare and assign type " + valueType + " to " + name);
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmtContext ctx) {
        visit(ctx.cond);

        final Label brJump = new Label();
        funcStmts.add(new Statement(Operation.JUMP_IF_FALSE, VALUE_STACK.pop(), brJump));

        visit(ctx.brTrue);

        if (ctx.brFalse == null) {
            brJump.setAddress(funcStmts.size());
        } else {
            final Label brEnd = new Label();
            funcStmts.add(new Statement(Operation.GOTO, brEnd));

            brJump.setAddress(funcStmts.size());

            visit(ctx.brFalse);
            brEnd.setAddress(funcStmts.size());
        }
        return null;
    }

    @Override
    public Tuple<String, Type> visitFArgument(FArgumentContext ctx) {
        return new Tuple<>(ctx.name.getText(), (Type) visit(ctx.value));
    }

    @Override
    public List<Tuple<String, Type>> visitFArgsList(FArgsListContext ctx) {
        final List<Tuple<String, Type>> dest = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i += 2) {
            dest.add((Tuple<String, Type>) visit(ctx.getChild(i)));
        }
        return dest;
    }

    @Override
    public Type visitFcallStmt(FcallStmtContext ctx) {
        final String base = ctx.base.getText();

        // Function pointer must go before its arguments
        // but since function name is not resolved until
        // later, we use hacks...
        final Mutable mut = new MutableCell();
        VALUE_STACK.push(mut);

        final List<Tuple<String, Type>> args = ctx.exprs == null ? Collections.EMPTY_LIST : visitFArgsList(ctx.exprs);
        final String fname = Module.makeFuncName(base, args.stream().map(Tuple::getA).toArray(String[]::new));
        final Type ftype = currentScope.get(fname);
        if (ftype == null) {
            throw new UndefinedSymbolException(fname);
        }

        // Update function pointer here (it is resolved)
        mut.setValue(Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, fname)));

        return performCall(ftype, args.stream().map(Tuple::getB).toArray(Type[]::new));
    }

    @Override
    public Type[] visitRArgsList(RArgsListContext ctx) {
        final Type[] dest = new Type[(ctx.getChildCount() + 1) / 2];
        for (int i = 0; i < dest.length; ++i) {
            dest[i] = (Type) visit(ctx.getChild(i * 2));
        }
        return dest;
    }

    @Override
    public Type[] visitRcallTail(RcallTailContext ctx) {
        return ctx.exprs == null ? new Type[0] : visitRArgsList(ctx.exprs);
    }

    @Override
    public Type visitRcallStmt(RcallStmtContext ctx) {
        return processRCalls(ctx.base, ctx.tail);
    }

    @Override
    public Type visitExprRefCall(ExprRefCallContext ctx) {
        return processRCalls(ctx.base, ctx.tail);
    }

    private Type processRCalls(ExprContext base, RcallTailContext tail) {
        final Type callable = (Type) visit(base);
        final Type[] args = visitRcallTail(tail);
        return performCall(callable, args);
    }

    private Type performCall(Type base, Type... args) {
        final Type result = base.tryPerformCall(args);
        if (result == null) {
            throw new UndefinedOperationException("Type " + base + " cannot be called with arguments: " + Arrays.toString(args));
        }

        // Try to perform the correct type conversions then push from right to left
        final FuncType ftype = (base instanceof FuncType) ? (FuncType) base : null;
        for (int i = 0; i < args.length; ++i) {
            Value val = VALUE_STACK.pop();
            Type argType = args[i];
            Type paramType = null;
            if (ftype != null && !argType.isSameType((paramType = ftype.params[i]))) {
                if (argType instanceof EnumKeyType) {
                    // hack: we redo the above routine as if argType was int
                    argType = TYPE_INT;
                }
                if (!argType.isSameType(paramType)) {
                    // Implement type casting
                    final String synthName = argType + "_" + paramType;
                    switch (synthName) {
                        case "byte_int":        val = applyRegisterTransfer(val, Operation.CONV_BYTE_INT); break;
                        case "short_int":       val = applyRegisterTransfer(val, Operation.CONV_SHORT_INT); break;
                        case "long_int":        val = applyRegisterTransfer(val, Operation.CONV_LONG_INT); break;
                        case "int_byte":        val = applyRegisterTransfer(val, Operation.CONV_INT_BYTE); break;
                        case "int_short":       val = applyRegisterTransfer(val, Operation.CONV_INT_SHORT); break;
                        case "int_long":        val = applyRegisterTransfer(val, Operation.CONV_INT_LONG); break;
                        case "int_float":       val = applyRegisterTransfer(val, Operation.CONV_INT_FLOAT); break;
                        case "long_float":      val = applyRegisterTransfer(val, Operation.CONV_LONG_FLOAT); break;
                        case "int_double":      val = applyRegisterTransfer(val, Operation.CONV_INT_DOUBLE); break;
                        case "long_double":     val = applyRegisterTransfer(val, Operation.CONV_LONG_DOUBLE); break;
                        case "float_double":    val = applyRegisterTransfer(val, Operation.CONV_FLOAT_DOUBLE); break;
                        default:
                            throw new AssertionError("Unknown conversion rule: " + synthName);
                    }
                }
            }
            funcStmts.add(new Statement(Operation.PUSH_PARAM, val));
        }
        final Register temporary = Register.makeTemporary();
        funcStmts.add(new Statement(Operation.CALL, VALUE_STACK.pop(), temporary));
        VALUE_STACK.push(temporary);

        LOGGER.info("Call to type " + base + " yields " + result);
        return result;
    }

    @Override
    public Type visitExprIfElse(ExprIfElseContext ctx) {
        visit(ctx.cond);

        // Each branch stores their result in this temporary
        // it's like a crappy phi function in SSA form
        final Register temporary = Register.makeTemporary();

        final Label brTrue = new Label();
        funcStmts.add(new Statement(Operation.JUMP_IF_TRUE, VALUE_STACK.pop(), brTrue));

        final Type b = (Type) visit(ctx.brFalse);
        funcStmts.add(new Statement(Operation.STORE_VAR, VALUE_STACK.pop(), temporary));

        final Label brEnd = new Label();
        funcStmts.add(new Statement(Operation.GOTO, brEnd));

        brTrue.setAddress(funcStmts.size());

        final Type a = (Type) visit(ctx.brTrue);
        funcStmts.add(new Statement(Operation.STORE_VAR, VALUE_STACK.pop(), temporary));

        brEnd.setAddress(funcStmts.size());

        VALUE_STACK.push(temporary);

        // a and b must have type in common
        if (a.canConvertTo(b)) {
            return b;
        } else if (b.canConvertTo(a)) {
            return a;
        } else {
            throw new IncompatibleTypeException(a, b);
        }
    }

    private Type processAssign(final ParseTree dest, final ParseTree tail) {
        final Type declType = (Type) visit(dest);
        final Value dummyValue = VALUE_STACK.pop();
        final Statement lastInstr = funcStmts.isEmpty() ? null : funcStmts.get(funcStmts.size() - 1);

        final Type valueType = (Type) visit(tail);
        if (!valueType.canConvertTo(declType)) {
            throw new IncompatibleTypeException(valueType, declType);
        }

        boolean validMove = false;
        if (dummyValue instanceof Register && !dummyValue.isTemporary()) {
            validMove = true;
            funcStmts.add(new Statement(Operation.STORE_VAR, VALUE_STACK.pop(), dummyValue));
            // Keep the register on the stack
            VALUE_STACK.push(dummyValue);
        } else if (lastInstr != null && lastInstr.op == Operation.GET_ATTR) {
            // R1 <- GET_ATTR R0, attr  ; new PUT_ATTR is the same (R1 is used as new value)
            validMove = true;
            funcStmts.add(new Statement(Operation.PUT_ATTR, lastInstr.lhs, lastInstr.rhs, VALUE_STACK.peek()));
        }

        if (!validMove) {
            throw new UndefinedOperationException("Bad storage pointer of " + dest.getText());
        }
        LOGGER.info("Assign type " + valueType + " to storage of type " + declType);
        return valueType;
    }

    private Type processMakeRef(final ParseTree value) {
        final Type valueType = (Type) visit(value);
        final Value dummyValue = VALUE_STACK.pop();
        final Statement lastInstr = funcStmts.isEmpty() ? null : funcStmts.get(funcStmts.size() - 1);

        boolean validMove = false;
        final Register temporary = Register.makeTemporary();
        if (dummyValue instanceof Register && !dummyValue.isTemporary()) {
            validMove = true;
            funcStmts.add(new Statement(Operation.REFER_VAR, dummyValue, temporary));
        } else if (lastInstr != null && lastInstr.op == Operation.GET_ATTR) {
            // R1 <- GET_ATTR R0, attr  ; new PUT_ATTR is the same (R1 is used as new value)
            validMove = true;
            funcStmts.add(new Statement(Operation.REFER_ATTR, lastInstr.lhs, lastInstr.rhs, temporary));
        }

        if (!validMove) {
            throw new UndefinedOperationException("Bad storage pointer of " + value.getText());
        }
        VALUE_STACK.push(temporary);
        LOGGER.info("Create reference to type " + valueType);
        return valueType;
    }

    @Override
    public Type visitExprAssign(ExprAssignContext ctx) {
        return processAssign(ctx.dest, ctx.value);
    }

    @Override
    public Type visitExprRelEql(ExprRelEqlContext ctx) {
        final Type lhs = (Type) visit(ctx.lhs);
        final Type rhs = (Type) visit(ctx.rhs);
        return dispatchBinaryOperator(lhs, ctx.op.getText(), rhs);
    }

    @Override
    public Type visitExprRelCmp(ExprRelCmpContext ctx) {
        final Type lhs = (Type) visit(ctx.lhs);
        final Type rhs = (Type) visit(ctx.rhs);
        return dispatchBinaryOperator(lhs, ctx.op.getText(), rhs);
    }

    @Override
    public Type visitExprAddSub(ExprAddSubContext ctx) {
        final Type lhs = (Type) visit(ctx.lhs);
        final Type rhs = (Type) visit(ctx.rhs);
        return dispatchBinaryOperator(lhs, ctx.op.getText(), rhs);
    }

    @Override
    public Type visitExprMulDivMod(ExprMulDivModContext ctx) {
        final Type lhs = (Type) visit(ctx.lhs);
        final Type rhs = (Type) visit(ctx.rhs);
        return dispatchBinaryOperator(lhs, ctx.op.getText(), rhs);
    }

    private static Operation[] convertSeqToLong(final Type t) {
        if (TYPE_BYTE.isSameType(t)) {
            return new Operation[] { Operation.CONV_BYTE_INT, Operation.CONV_INT_LONG };
        }
        if (TYPE_SHORT.isSameType(t)) {
            return new Operation[] { Operation.CONV_SHORT_INT, Operation.CONV_INT_LONG };
        }
        if (TYPE_INT.isSameType(t)) {
            return new Operation[] { Operation.CONV_INT_LONG };
        }
        if (TYPE_LONG.isSameType(t)) {
            return new Operation[] { Operation.NOP };
        }
        return null;
    }

    private static Operation[] uncastLongSequence(final boolean useLong, final Type t) {
        if (TYPE_BYTE.isSameType(t)) {
            return new Operation[] { useLong ? Operation.CONV_LONG_INT : Operation.NOP, Operation.CONV_INT_BYTE };
        }
        if (TYPE_SHORT.isSameType(t)) {
            return new Operation[] { useLong ? Operation.CONV_LONG_INT : Operation.NOP, Operation.CONV_INT_SHORT };
        }
        if (TYPE_INT.isSameType(t)) {
            return new Operation[] { useLong ? Operation.CONV_LONG_INT : Operation.NOP };
        }
        return new Operation[0];
    }

    private static Operation[] convertSeqToFloat(final Type t) {
        final Operation[] temp = convertSeqToLong(t);
        if (temp == null) {
            if (TYPE_FLOAT.isSameType(t)) {
                return new Operation[] { Operation.NOP };
            }
            return null;
        }

        // Ints should directly go to float
        if (temp[temp.length - 1] == Operation.CONV_INT_LONG) {
            temp[temp.length - 1] = Operation.CONV_INT_FLOAT;
            return temp;
        }

        final Operation[] actualConv = Arrays.copyOf(temp, temp.length + 1);
        actualConv[actualConv.length - 1] = Operation.CONV_LONG_FLOAT;
        return actualConv;
    }

    private static Operation[] convertSeqToDouble(final Type t) {
        final Operation[] temp = convertSeqToLong(t);
        if (temp == null) {
            if (TYPE_FLOAT.isSameType(t)) {
                return new Operation[] { Operation.CONV_FLOAT_DOUBLE };
            }
            if (TYPE_DOUBLE.isSameType(t)) {
                return new Operation[] { Operation.NOP };
            }
            return null;
        }

        // Ints should directly go to double
        if (temp[temp.length - 1] == Operation.CONV_INT_LONG) {
            temp[temp.length - 1] = Operation.CONV_INT_DOUBLE;
            return temp;
        }

        final Operation[] actualConv = Arrays.copyOf(temp, temp.length + 1);
        actualConv[actualConv.length - 1] = Operation.CONV_LONG_DOUBLE;
        return actualConv;
    }

    private Type dispatchBinaryOperator(Type lhs, String name, Type rhs) {
        final BinaryOperator op = BIN_OP_MAPPING.get(name);
        if (op == null) {
            throw new AssertionError("Unknown binary operator " + name);
        }

        final Type result = lhs.tryPerformBinary(op, rhs);
        if (result == null) {
            throw new UndefinedOperationException("Type " + lhs + " does not support binary operator " + name + " with " + rhs);
        }

        final Value temporary = Register.makeTemporary();
        Value a = VALUE_STACK.pop();  // value of rhs
        Value b = VALUE_STACK.pop();  // value of lhs
        Operation opcode = null;

        Operation[] cleanupSeq = new Operation[0];

        Operation[] aSeq, bSeq;
        if ((aSeq = convertSeqToLong(rhs)) != null
                && (bSeq = convertSeqToLong(lhs)) != null) {
            boolean useLong = true;

            // Insert the conversion sequences
            if (aSeq[aSeq.length - 1] == Operation.CONV_INT_LONG
                    && bSeq[bSeq.length - 1] == Operation.CONV_INT_LONG) {
                // convert to int is enough
                aSeq[aSeq.length - 1] = bSeq[bSeq.length - 1] = Operation.NOP;
                useLong = false;
            }

            a = applyRegisterTransfer(a, aSeq);
            b = applyRegisterTransfer(b, bSeq);

            switch (op) {
                case ADD: opcode = useLong ? Operation.LONG_ADD : Operation.INT_ADD; break;
                case SUB: opcode = useLong ? Operation.LONG_SUB : Operation.INT_SUB; break;
                case MUL: opcode = useLong ? Operation.LONG_MUL : Operation.INT_MUL; break;
                case DIV: opcode = useLong ? Operation.LONG_DIV : Operation.INT_DIV; break;
                case MOD: opcode = useLong ? Operation.LONG_MOD : Operation.INT_MOD; break;
            }

            // Downcast back to actual type is necessary
            cleanupSeq = uncastLongSequence(useLong, result);
        } else if ((aSeq = convertSeqToFloat(rhs)) != null
                && (bSeq = convertSeqToFloat(lhs)) != null) {
            a = applyRegisterTransfer(a, aSeq);
            b = applyRegisterTransfer(b, bSeq);

            switch (op) {
                case ADD: opcode = Operation.FLOAT_ADD; break;
                case SUB: opcode = Operation.FLOAT_SUB; break;
                case MUL: opcode = Operation.FLOAT_MUL; break;
                case DIV: opcode = Operation.FLOAT_DIV; break;
                case MOD: opcode = Operation.FLOAT_MOD; break;
            }
        } else if ((aSeq = convertSeqToDouble(rhs)) != null
                && (bSeq = convertSeqToDouble(lhs)) != null) {
            a = applyRegisterTransfer(a, aSeq);
            b = applyRegisterTransfer(b, bSeq);

            switch (op) {
                case ADD: opcode = Operation.DOUBLE_ADD; break;
                case SUB: opcode = Operation.DOUBLE_SUB; break;
                case MUL: opcode = Operation.DOUBLE_MUL; break;
                case DIV: opcode = Operation.DOUBLE_DIV; break;
                case MOD: opcode = Operation.DOUBLE_MOD; break;
            }
        }

        if (opcode == null) {
            // TODO: Remove this switch block!
            switch (op) {
                case LESSER_THAN:    opcode = Operation.BINARY_LESSER_THAN; break;
                case GREATER_THAN:   opcode = Operation.BINARY_GREATER_THAN; break;
                case LESSER_EQUALS:  opcode = Operation.BINARY_LESSER_EQUALS; break;
                case GREATER_EQUALS: opcode = Operation.BINARY_GREATER_EQUALS; break;
                case EQUALS:         opcode = Operation.BINARY_EQUALS; break;
                case NOT_EQUALS:     opcode = Operation.BINARY_NOT_EQUALS; break;
                default:
                    throw new AssertionError("Compiler failed to synthesize " + op + " for " + lhs + " and " + rhs);
            }
        }

        funcStmts.add(new Statement(opcode, b, a, temporary));
        VALUE_STACK.push(applyRegisterTransfer(temporary, cleanupSeq));

        LOGGER.info(lhs + " " + name + " " + rhs + " yields " + result);
        return result;
    }

    private Value applyRegisterTransfer(Value source, Operation... steps) {
        for (final Operation step : steps) {
            if (step != Operation.NOP) {
                final Value dest = Register.makeTemporary();
                funcStmts.add(new Statement(step, source, dest));
                source = dest;  // perform transfer
            }
        }
        return source;
    }

    @Override
    public Type visitExprAccess(ExprAccessContext ctx) {
        final String attr = ctx.attr.getText();
        final Type base = (Type) visit(ctx.base);

        final Type result = base.tryAccessAttribute(attr);
        if (result == null) {
            throw new UndefinedOperationException("Type " + base + " does not allow accessing attribute " + attr);
        }

        final Register temporary = Register.makeTemporary();

        LOGGER.info(base + "." + attr + " yields " + result);
        funcStmts.add(new Statement(
                base instanceof EnumType ? Operation.LOAD_ENUM_KEY : Operation.GET_ATTR,
                VALUE_STACK.pop(),
                Register.makeNamed(attr), temporary));
        VALUE_STACK.push(temporary);
        return result;
    }

    @Override
    public Type visitExprUnary(ExprUnaryContext ctx) {
        final String name = ctx.op.getText();
        if (name.equals("&")) {
            return processMakeRef(ctx.rhs);
        }

        final UnaryOperator op = UNI_OP_MAPPING.get(name);
        if (op == null) {
            throw new AssertionError("Unknown unary operator " + name);
        }

        final Type base = (Type) visit(ctx.rhs);
        final Type result = base.tryPerformUnary(op);
        if (result == null) {
            throw new UndefinedOperationException("Type " + base + " does not support unary operator " + name);
        }

        final Value temporary = Register.makeTemporary();
        Value value = VALUE_STACK.pop();
        Operation opcode = null;

        Operation[] cleanupSeq = new Operation[0];

        final Operation[] baseSeq = convertSeqToLong(base);

        if (base != null) {
            boolean useLong = true;
            if (baseSeq[baseSeq.length - 1] == Operation.CONV_INT_LONG) {
                // Convert to int is enough
                baseSeq[baseSeq.length - 1] = Operation.NOP;
                useLong = false;
            }

            value = applyRegisterTransfer(value, baseSeq);

            switch (op) {
                case ADD:   opcode = Operation.STORE_VAR; break;
                case SUB:   opcode = useLong ? Operation.LONG_NEG : Operation.INT_NEG; break;
                case TILDA: opcode = useLong ? Operation.LONG_CPL : Operation.INT_CPL; break;
            }

            // Downcast back to actual type is necessary
            cleanupSeq = uncastLongSequence(useLong, result);
        }

        if (opcode == null) {
            throw new AssertionError("Compiler failed to synthesize " + op + " for " + base);
        }
        funcStmts.add(new Statement(opcode, value, temporary));
        VALUE_STACK.push(applyRegisterTransfer(temporary, cleanupSeq));

        LOGGER.info(name + " " + base + " yields " + result);
        return result;
    }

    @Override
    public Type visitExprSymbol(ExprSymbolContext ctx) {
        final String symbol = (String) visit(ctx.getChild(0));

        LOGGER.info("Looking up symbol with internal name " + symbol);
        final Type symType = currentScope.get(symbol);

        if (symType == null) {
            throw new UndefinedSymbolException(symbol);
        }

        VALUE_STACK.push(Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, symbol)));

        return symType;
    }

    @Override
    public Type visitExprBool(ExprBoolContext ctx) {
        final String text = ctx.getText();

        final Register temporary = Register.makeTemporary();
        funcStmts.add(new Statement("true".equals(text) ? Operation.LOAD_TRUE : Operation.LOAD_FALSE, temporary));
        VALUE_STACK.push(temporary);

        LOGGER.info("Literal " + text + " is a bool");
        return UnaryType.getType("bool");
    }

    @Override
    public Type visitExprNumber(ExprNumberContext ctx) {
        String text = ctx.getText();

        final Tuple<String, Integer> info = NUM_LIT_INFO.get(Character.toLowerCase(text.charAt(text.length() - 1)));

        final int size;
        final String typeName;
        if (info == null) {
            if (text.contains(".")) {
                typeName = "double";
                size = Double.SIZE;
            } else {
                typeName = "int";
                size = Integer.SIZE;
            }
        } else {
            text = text.substring(0, text.length() - 1);
            size = info.getB();
            switch ((typeName = info.getA())) {
                case "float":
                case "double":
                    if (!text.contains(".")) {
                        text += ".0";
                    }
                    break;
            }
        }

        final Register temporary = Register.makeTemporary();
        funcStmts.add(new Statement(Operation.LOAD_NUMERAL, new Fixnum(text, size), temporary));
        VALUE_STACK.push(temporary);

        LOGGER.info("Literal " + text + " is a " + typeName);
        return UnaryType.getType(typeName);
    }

    @Override
    public Type visitExprAllocStruct(ExprAllocStructContext ctx) {
        final String structName = ctx.t.getText();
        final Module.Entry ent = currentModule.get(structName);
        if (ent == null) {
            throw new UndefinedSymbolException(structName);
        }
        if (ent.isType && ent.type instanceof StructType) {
            final StructType newData = ((StructType) ent.type).allocate();
            final Register temp = Register.makeTemporary();
            funcStmts.add(new Statement(
                    Operation.ALLOC_STRUCT,
                    Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, structName)),
                    temp));
            VALUE_STACK.push(temp);
            return newData;
        }
        throw new UndefinedOperationException("Cannot allocate non-struct type " + structName);
    }

    @Override
    public Type visitExprParenthesis(ExprParenthesisContext ctx) {
        return (Type) visit(ctx.inner);
    }
}