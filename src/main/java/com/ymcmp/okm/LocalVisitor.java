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

    private static final Fixnum INT_ZERO = new Fixnum(0, Integer.SIZE);

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

    private Label currentLoopHead;
    private Label currentLoopEnd;

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
            initializer.add(new Statement(Operation.CALL_UNIT, Register.makeNamed(func)));
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
            for (final Map.Entry<String, Type> param : currentScope.getCurrentLocals()) {
                final Register slot = Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, param.getKey()));
                final Type t = param.getValue();
                final Statement stmt = new Statement(t.isFloatPoint() ? Operation.POP_PARAM_FLOAT : Operation.POP_PARAM_INT, slot);
                stmt.setDataSize(t.getSize());
                funcStmts.add(stmt);
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

            // In addition, in anything jumps beyond the function's body, it also means function failed to return
            boolean appendReturn = false;
            for (final Statement jmpOp : funcStmts) {
                if (jmpOp.op.branchesToAddress()) {
                    final Label label = (Label) jmpOp.dst;
                    if (label.getAddress() >= funcStmts.size()) {
                        // If the return type is unit, we will add it
                        if (conformingType.isSameType(UnaryType.getType("unit"))) {
                            // Just in case for some reason the function ends at 10 and it jumps to 20
                            label.setAddress(funcStmts.size());
                            appendReturn = true;
                        } else {
                            throw new RuntimeException("Function " + currentScope.functionName + " does not return!");
                        }
                    }
                }
            }
            if (appendReturn) funcStmts.add(new Statement(Operation.RETURN_UNIT));

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
        if (ctx.inner == null) {
            final String name = ctx.getText();
            final Module.Entry ent = currentModule.get(name);
            if (ent != null && ent.isType) {
                final Type t = ent.type;
                if (t instanceof EnumType) {
                    return ((EnumType) t).createCorrespondingKey();
                }
                return t;
            }
            throw new UndefinedOperationException(name + " does not name a type");
        }
        return new Pointer(visitType(ctx.inner));
    }

    @Override
    public List<Tuple<String, Type>> visitParameter(ParameterContext ctx) {
        final List<Tuple<String, Type>> list = new ArrayList<>();
        final Type type = visitType(ctx.t);
        for (int i = 0; i < ctx.getChildCount() - 2; i += 2) {
            list.add(new Tuple<>(ctx.getChild(i).getText(), type.allocate()));
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
        this.currentStruct = null;
        return null;
    }

    private void processReturn(Type maybeNull) {
        final Type valueType = maybeNull == null ? UnaryType.getType("unit") : maybeNull;
        if (!valueType.canConvertTo(conformingType)) {
            throw new IncompatibleTypeException(valueType, conformingType);
        }

        final Statement stmt;
        if (valueType.isSameType(UnaryType.getType("unit"))) {
            stmt = new Statement(Operation.RETURN_UNIT);
        } else {
            final Value onStack = VALUE_STACK.pop();
            stmt = new Statement(conformingType.isFloatPoint() ? Operation.RETURN_FLOAT : Operation.RETURN_INT, insertConversion(onStack, valueType, conformingType));
            stmt.setDataSize(valueType.getSize());
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

        final Statement stmt = new Statement(
                Operation.STORE_VAR,
                VALUE_STACK.pop(),
                Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, name)));
        stmt.setDataSize(valueType.getSize());
        funcStmts.add(stmt);

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
    public Object visitLoopStmt(LoopStmtContext ctx) {
        // Save
        final Label oldLoopHead = currentLoopHead;
        final Label oldLoopEnd = currentLoopEnd;

        currentLoopHead = new Label(funcStmts.size());
        currentLoopEnd = new Label();

        visit(ctx.cond);
        funcStmts.add(new Statement(Operation.JUMP_IF_FALSE, VALUE_STACK.pop(), currentLoopEnd));

        final int startScanningIndex = funcStmts.size();
        visit(ctx.body);
        final int endScanningIndex = funcStmts.size();

        funcStmts.add(new Statement(Operation.GOTO, currentLoopHead));
        currentLoopEnd.setAddress(funcStmts.size());

        for (int i = startScanningIndex; i < endScanningIndex; ++i) {
            final Statement stmt = funcStmts.get(i);
            if (stmt.dst == currentLoopEnd) {
                funcStmts.set(i, new Statement(stmt.op, stmt.lhs, stmt.rhs, currentLoopEnd.duplicate()));
            }
        }

        // Restore
        currentLoopEnd = oldLoopEnd;
        currentLoopHead = oldLoopHead;

        return null;
    }

    @Override
    public Object visitBreakStmt(BreakStmtContext ctx) {
        if (currentLoopEnd == null) {
            throw new UndefinedOperationException("Cannot use break outside of loop context");
        }
        funcStmts.add(new Statement(Operation.GOTO, currentLoopEnd));
        return null;
    }

    @Override
    public Object visitContStmt(ContStmtContext ctx) {
        if (currentLoopHead == null) {
            throw new UndefinedOperationException("Cannot use continue outside of loop context");
        }
        funcStmts.add(new Statement(Operation.GOTO, currentLoopHead));
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

    private Value insertConversion(Value val, Type src, Type dst) {
        // Since java does not support tailcalls and probably never will,
        // we will hack one in here (its only self-recursing anyway)
        tailcall: while (true) {
            if (!src.isSameType(dst)) {
                if (src instanceof EnumKeyType) {
                    src = TYPE_INT;
                    continue tailcall;
                }

                if (src instanceof Pointer) {
                    // POINTER_GET first
                    Type newSrc = src;
                    while (newSrc instanceof Pointer) {
                        newSrc = ((Pointer) newSrc).inner;
                        final Register tmp = Register.makeTemporary();
                        final Statement derefStmt = new Statement(Operation.POINTER_GET, val, tmp);
                        derefStmt.setDataSize(newSrc.getSize());
                        funcStmts.add(derefStmt);
                        val = tmp;
                        if (newSrc.isSameType(dst)) {
                            return val;
                        }
                    }

                    src = newSrc;
                    continue tailcall;
                }

                // Implement type casting
                final String synthName = src + "_" + dst;
                switch (synthName) {
                    // Single instruction conversions:
                    case "byte_int":     return applyRegisterTransfer(val, Operation.CONV_BYTE_INT);
                    case "short_int":    return applyRegisterTransfer(val, Operation.CONV_SHORT_INT);
                    case "long_int":     return applyRegisterTransfer(val, Operation.CONV_LONG_INT);
                    case "int_byte":     return applyRegisterTransfer(val, Operation.CONV_INT_BYTE);
                    case "int_short":    return applyRegisterTransfer(val, Operation.CONV_INT_SHORT);
                    case "int_long":     return applyRegisterTransfer(val, Operation.CONV_INT_LONG);
                    case "int_float":    return applyRegisterTransfer(val, Operation.CONV_INT_FLOAT);
                    case "long_float":   return applyRegisterTransfer(val, Operation.CONV_LONG_FLOAT);
                    case "int_double":   return applyRegisterTransfer(val, Operation.CONV_INT_DOUBLE);
                    case "long_double":  return applyRegisterTransfer(val, Operation.CONV_LONG_DOUBLE);
                    case "float_double": return applyRegisterTransfer(val, Operation.CONV_FLOAT_DOUBLE);

                    // Multi-instruction conversions:
                    case "byte_short":   return applyRegisterTransfer(val, Operation.CONV_BYTE_INT, Operation.CONV_INT_SHORT);
                    case "byte_long":    return applyRegisterTransfer(val, Operation.CONV_BYTE_INT, Operation.CONV_INT_LONG);
                    case "byte_float":   return applyRegisterTransfer(val, Operation.CONV_BYTE_INT, Operation.CONV_INT_FLOAT);
                    case "byte_double":  return applyRegisterTransfer(val, Operation.CONV_BYTE_INT, Operation.CONV_INT_DOUBLE);
                    case "short_long":   return applyRegisterTransfer(val, Operation.CONV_SHORT_INT, Operation.CONV_INT_LONG);
                    case "short_float":  return applyRegisterTransfer(val, Operation.CONV_SHORT_INT, Operation.CONV_INT_FLOAT);
                    case "short_double": return applyRegisterTransfer(val, Operation.CONV_SHORT_INT, Operation.CONV_INT_DOUBLE);

                    // Failure case:
                    default:
                        throw new AssertionError("Unknown conversion rule: " + synthName);
                }
            }
            return val;
        }
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
            if (ftype != null) {
                val = insertConversion(val, argType, (paramType = ftype.params[i]));
            }
            final Statement stmt = new Statement(Operation.PUSH_PARAM, val);
            stmt.setDataSize(paramType.getSize());
            funcStmts.add(stmt);
        }
        final Statement stmt;
        final Value temporary;
        if (result.isSameType(UnaryType.getType("unit"))) {
            // if the function returns unit, we use CALL_UNIT instead
            // which does not use temporaries
            temporary = Fixnum.FALSE;   // really, any pure value will work
            stmt = new Statement(Operation.CALL_UNIT, VALUE_STACK.pop());
        } else {
            temporary = Register.makeTemporary();
            stmt = new Statement(Operation.CALL, VALUE_STACK.pop(), temporary);
            stmt.setDataSize(result.getSize());
        }
        funcStmts.add(stmt);
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

        // unit functions
        int numOfUnitFuncs = 0;

        final Label brTrue = new Label();
        funcStmts.add(new Statement(Operation.JUMP_IF_TRUE, VALUE_STACK.pop(), brTrue));

        final Type b = (Type) visit(ctx.brFalse);
        final Value valB = VALUE_STACK.pop();;
        final Statement stmtB;
        if (b.isSameType(UnaryType.getType("unit"))) {
            stmtB = new Statement(Operation.NOP);
            ++numOfUnitFuncs;
        } else {
            stmtB = new Statement(Operation.STORE_VAR, valB, temporary);
            stmtB.setDataSize(b.getSize());
        }
        funcStmts.add(stmtB);

        final Label brEnd = new Label();
        funcStmts.add(new Statement(Operation.GOTO, brEnd));

        brTrue.setAddress(funcStmts.size());

        final Type a = (Type) visit(ctx.brTrue);
        final Value valA = VALUE_STACK.pop();;
        final Statement stmtA;
        if (a.isSameType(UnaryType.getType("unit"))) {
            stmtA = new Statement(Operation.NOP);
            ++numOfUnitFuncs;
        } else {
            stmtA = new Statement(Operation.STORE_VAR, valA, temporary);
            stmtA.setDataSize(a.getSize());
        }
        funcStmts.add(stmtA);

        // a and b must have type in common
        Type mergedType;
        if (numOfUnitFuncs == 2) {
            // Both branches are unit, no need for conversions
            mergedType = a;
        } else if (a.canConvertTo(b)) {
            // Insert conversion sequence for a, since a was the latest statement,
            // we mutate that directly!
            funcStmts.remove(funcStmts.size() - 1);
            final Statement newStmtA = new Statement(Operation.STORE_VAR, insertConversion(stmtA.lhs, a, b), temporary);
            newStmtA.setDataSize(b.getSize());
            funcStmts.add(newStmtA);
            mergedType = b;
        } else if (b.canConvertTo(a)) {
            // Insert conversion sequence for b. Instead of overwriting the last
            // instruction, we will *save* from GOTO to the end, insert the
            // conversion sequence, then put it back. Of course, update the jump
            // addresses, too!
            final List<Statement> view = funcStmts.subList(brTrue.getAddress() - 1, funcStmts.size());
            final List<Statement> save = new ArrayList<>(view);
            view.clear();

            funcStmts.remove(funcStmts.size() - 1);
            final Statement newStmtB = new Statement(Operation.STORE_VAR, insertConversion(stmtB.lhs, b, a), temporary);
            newStmtB.setDataSize(a.getSize());
            funcStmts.add(newStmtB);

            final int newBranchAddress = funcStmts.size();

            funcStmts.addAll(save);
            brTrue.setAddress(newBranchAddress + 1);
            mergedType = a;
        } else {
            throw new IncompatibleTypeException(a, b);
        }

        brEnd.setAddress(funcStmts.size());

        VALUE_STACK.push(temporary);

        return mergedType;
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
            final Value convertedValue = insertConversion(VALUE_STACK.pop(), valueType, declType);
            final Statement stmt = new Statement(Operation.STORE_VAR, convertedValue, dummyValue);
            stmt.setDataSize(valueType.getSize());
            funcStmts.add(stmt);
            // Keep the register on the stack
            VALUE_STACK.push(dummyValue);
        } else if (lastInstr != null) {
            switch (lastInstr.op) {
                case GET_ATTR: {
                    // R1 <- GET_ATTR R0, attr  ; new PUT_ATTR is the same (R1 is used as new value)
                    validMove = true;
                    final Value convertedValue = insertConversion(VALUE_STACK.pop(), valueType, declType);
                    final Statement stmt = new Statement(Operation.PUT_ATTR, lastInstr.lhs, lastInstr.rhs, convertedValue);
                    stmt.setDataSize(valueType.getSize());
                    funcStmts.add(stmt);
                    VALUE_STACK.push(convertedValue);
                    break;
                }
                case DEREF_GET_ATTR: {
                    // R1 <- DEREF_GET_ATTR R0, attr ; new DEREF_PUT_ATTR is the same (R1 is used as new value)
                    validMove = true;
                    final Value convertedValue = insertConversion(VALUE_STACK.pop(), valueType, declType);
                    final Statement stmt = new Statement(Operation.DEREF_PUT_ATTR, lastInstr.lhs, lastInstr.rhs, convertedValue);
                    stmt.setDataSize(valueType.getSize());
                    funcStmts.add(stmt);
                    VALUE_STACK.push(convertedValue);
                    break;
                }
                case POINTER_GET: {
                    // R1 <- POINTER_GET R0
                    validMove = true;
                    final Value convertedValue = insertConversion(VALUE_STACK.pop(), valueType, declType);
                    final Statement stmt = new Statement(Operation.POINTER_PUT, convertedValue, lastInstr.lhs);
                    stmt.setDataSize(valueType.getSize());
                    funcStmts.add(stmt);
                    VALUE_STACK.push(convertedValue);
                    break;
                }
            }
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
        final Pointer ptr = new Pointer(valueType);
        LOGGER.info("Create " + ptr);
        return ptr;
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
        if (TYPE_INT.isSameType(t) || t instanceof EnumKeyType) {
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

            boolean generateCleanup = false;
            switch (op) {
                case ADD: opcode = useLong ? Operation.LONG_ADD : Operation.INT_ADD; generateCleanup = true; break;
                case SUB: opcode = useLong ? Operation.LONG_SUB : Operation.INT_SUB; generateCleanup = true; break;
                case MUL: opcode = useLong ? Operation.LONG_MUL : Operation.INT_MUL; generateCleanup = true; break;
                case DIV: opcode = useLong ? Operation.LONG_DIV : Operation.INT_DIV; generateCleanup = true; break;
                case MOD: opcode = useLong ? Operation.LONG_MOD : Operation.INT_MOD; generateCleanup = true; break;
                case LESSER_THAN:    opcode = useLong ? Operation.LONG_CMP : Operation.INT_LT; break;
                case GREATER_THAN:   opcode = useLong ? Operation.LONG_CMP : Operation.INT_GT; break;
                case LESSER_EQUALS:  opcode = useLong ? Operation.LONG_CMP : Operation.INT_LE; break;
                case GREATER_EQUALS: opcode = useLong ? Operation.LONG_CMP : Operation.INT_GE; break;
                case EQUALS:         opcode = useLong ? Operation.LONG_CMP : Operation.INT_EQ; break;
                case NOT_EQUALS:     opcode = useLong ? Operation.LONG_CMP : Operation.INT_NE; break;
            }

            if (generateCleanup) {
                // Downcast back to actual type is necessary
                cleanupSeq = uncastLongSequence(useLong, result);
            }
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
                case LESSER_THAN:
                case GREATER_THAN:
                case LESSER_EQUALS:
                case GREATER_EQUALS:
                case EQUALS:
                case NOT_EQUALS:
                    opcode = Operation.FLOAT_CMP;
                    break;
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
                case LESSER_THAN:
                case GREATER_THAN:
                case LESSER_EQUALS:
                case GREATER_EQUALS:
                case EQUALS:
                case NOT_EQUALS:
                    opcode = Operation.DOUBLE_CMP;
                    break;
            }
        }

        if (opcode == null) {
            throw new AssertionError("Compiler failed to synthesize " + op + " for " + lhs + " and " + rhs);
        }

        if (opcode.isCmp()) {
            // These opcodes return an integer instead of a boolean
            // an additional INT_* comparison is needed
            final Register cmpValue = Register.makeTemporary();
            funcStmts.add(new Statement(opcode, b, a, cmpValue));
            switch (op) {
                case LESSER_THAN:       // cmpValue < 0
                    funcStmts.add(new Statement(Operation.INT_LT, cmpValue, INT_ZERO, temporary));
                    break;
                case GREATER_THAN:
                    funcStmts.add(new Statement(Operation.INT_GT, cmpValue, INT_ZERO, temporary));
                    break;
                case LESSER_EQUALS:
                    funcStmts.add(new Statement(Operation.INT_LE, cmpValue, INT_ZERO, temporary));
                    break;
                case GREATER_EQUALS:
                    funcStmts.add(new Statement(Operation.INT_GE, cmpValue, INT_ZERO, temporary));
                    break;
                case EQUALS:
                    funcStmts.add(new Statement(Operation.INT_EQ, cmpValue, INT_ZERO, temporary));
                    break;
                case NOT_EQUALS:
                    funcStmts.add(new Statement(Operation.INT_NE, cmpValue, INT_ZERO, temporary));
                    break;
                default:
                    throw new AssertionError("Unknown additional comparison " + op);
            }
        } else {
            funcStmts.add(new Statement(opcode, b, a, temporary));
        }
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

        boolean isPointer = false;
        Type result = base.tryAccessAttribute(attr);
        if (result == null) {
            // It might be a pointer. in which case we un-pointer it
            // and see it it works
            Value ptr = null;
            block: {
                if (base instanceof Pointer) {
                    isPointer = true;
                    ptr = VALUE_STACK.pop();
                    Type newBase = base;
                    while (newBase instanceof Pointer) {
                        newBase = ((Pointer) newBase).inner;
                        if ((result = newBase.tryAccessAttribute(attr)) != null) {
                            break block;
                        }

                        final Register temp = Register.makeTemporary();
                        final Statement deref = new Statement(Operation.POINTER_GET, ptr, temp);
                        deref.setDataSize(newBase.getSize());
                        funcStmts.add(deref);
                        ptr = temp;
                    }
                    throw new UndefinedOperationException("Type " + newBase + " does not allow accessing attribute " + attr);
                }
                throw new UndefinedOperationException("Type " + base + " does not allow accessing attribute " + attr);
            }
            // If control flow reaches here, ptr *must* not be null
            VALUE_STACK.push(ptr);
        }

        final Register temporary = Register.makeTemporary();

        LOGGER.info(base + "." + attr + " yields " + result);
        final Statement stmt;
        block: {
            if (base instanceof EnumType) {
                final EnumType enumBase = (EnumType) base;
                final String[] keys = enumBase.getKeys();
                for (int ordinal = 0; ordinal < keys.length; ++ordinal) {
                    if (attr.equals(keys[ordinal])) {
                        stmt = new Statement(Operation.LOAD_NUMERAL, new Fixnum(ordinal, Integer.SIZE), temporary);
                        break block;
                    }
                }
                throw new AssertionError("Unkown enum key of " + attr + " in type " + enumBase);
            }
            stmt = new Statement(
                    isPointer ? Operation.DEREF_GET_ATTR : Operation.GET_ATTR,
                    VALUE_STACK.pop(),
                    Register.makeNamed(attr),
                    temporary);
        }
        stmt.setDataSize(result.getSize());
        funcStmts.add(stmt);
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

        if (baseSeq != null) {
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

        if (op == UnaryOperator.TILDA && base instanceof Pointer) {
            opcode = Operation.POINTER_GET;
        }

        if (opcode == null) {
            throw new AssertionError("Compiler failed to synthesize " + op + " for " + base);
        }

        final Statement stmt = new Statement(opcode, value, temporary);
        stmt.setDataSize(result.getSize());
        funcStmts.add(stmt);
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

        if (!(symType instanceof EnumType)) {
            VALUE_STACK.push(Register.makeNamed(currentScope.getProcessedName(NAMING_STRAT, symbol)));
        }

        return symType;
    }

    @Override
    public Type visitExprBool(ExprBoolContext ctx) {
        final String text = ctx.getText();

        final Register temporary = Register.makeTemporary();
        final Type t = UnaryType.getType("bool");
        final Statement stmt = new Statement("true".equals(text) ? Operation.LOAD_TRUE : Operation.LOAD_FALSE, temporary);
        stmt.setDataSize(t.getSize());
        funcStmts.add(stmt);
        VALUE_STACK.push(temporary);

        LOGGER.info("Literal " + text + " is a bool");
        return t;
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

        final Type t = UnaryType.getType(typeName);
        final Register temporary = Register.makeTemporary();
        final Statement stmt = new Statement(Operation.LOAD_NUMERAL, new Fixnum(text, size), temporary);
        stmt.setDataSize(t.getSize());
        funcStmts.add(stmt);
        VALUE_STACK.push(temporary);

        LOGGER.info("Literal " + text + " is a " + typeName);
        return t;
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
            final Statement stmt = new Statement(Operation.ALLOC_STRUCT, temp);
            stmt.setDataSize(newData.getSize());
            funcStmts.add(stmt);
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