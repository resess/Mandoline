package ca.ubc.ece.resess.slicer.dynamic.core.accesspath;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.SootMethod;
import soot.Type;
import soot.toolkits.scalar.Pair;
import ca.ubc.ece.resess.slicer.dynamic.core.exceptions.NullTypeException;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
import ca.ubc.ece.resess.slicer.dynamic.core.exceptions.EmptyAccessPathException;


public class AccessPath {
    private ArrayList<String> path = new ArrayList<>();
    private ArrayList<Type> classPath = new ArrayList<>();
    private boolean isStaticField = false;
    private boolean clipped = false;
    private int usedAt = -1;
    private int definedAt = -1;
    private StatementInstance si = null;
    private AccessPath addedSide = null;
    private static final String TERMINATOR = "*";
    public static final int NOT_DEFINED = -1;
    public static final int NOT_USED = -1;
    
    public AccessPath(int usedAt, int definedAt, StatementInstance si){
        setPath(new ArrayList<>());
        setClassPath(new ArrayList<>());
        setUsedLine(usedAt);
        setDefinedLine(definedAt);
        setStatementInstance(si);
    }

    public AccessPath(AccessPath other, StatementInstance si){
        setPath(other.getPath());
        if(other.isStaticField) {
            setStaticField();
        }
        setClassPath(other.getClassPath());
        setUsedLine(other.getUsedLine());
        setDefinedLine(other.getDefinedLine());
        setStatementInstance(si);
    }

    public AccessPath(AccessPath other, int usedAt, int definedAt, StatementInstance si){
        setPath(other.getPath());
        if(other.isStaticField) {
            setStaticField();
        }
        setClassPath(other.getClassPath());
        setUsedLine(usedAt);
        setDefinedLine(definedAt);
        setStatementInstance(si);
    }

    public AccessPath(String path, Type type, int usedAt, int definedAt, StatementInstance si){
        setPath(path);
        setUsedLine(usedAt);
        setDefinedLine(definedAt);
        this.classPath.add(type);
        if (type == null){
            throw new NullTypeException();
        }
        setStatementInstance(si);
    }

    public void setUsedLine(int lineNo) {
        this.usedAt = lineNo;
    }

    public void setDefinedLine(int definedAt) {
        this.definedAt = definedAt;
    }

    public int getUsedLine() {
        return usedAt;
    }

    public int getDefinedLine() {
        return definedAt;
    }

    public void setStaticField() {
        this.isStaticField = true;
    }

    public boolean isStaticField() {
        return isStaticField;
    }

    private void setClassPath(List<Type> classPath) {
        this.classPath = new ArrayList<>(classPath);
        clipAccessPath();
    }

    private void setPath(String path) {
        if (path.startsWith("$")) {
            path = path.substring(1);
        }
        this.path = new ArrayList<>();
        this.path.add(path);
        clipAccessPath();
    }

    private void setPath(List<String> path) {
        this.path = new ArrayList<>(path);
        clipAccessPath();
    }

    public void setStatementInstance(StatementInstance si) {
        this.si = si;
    }

    public void setAddedSide(AccessPath addedSide) {
        this.addedSide = addedSide;
    }

    public int length() {
        return path.size();
    }

    public List<String> getPath() {
        return path;
    }

    public String getPathString(){
        return String.join(".", this.path) + (this.clipped? ("."+TERMINATOR):(""));
    }

    public List<Type> getClassPath() {
        return classPath;
    }

    public StatementInstance getStatementInstance() {
        return si;
    }

    public boolean startsWith(String base) {
        base = base.startsWith("$")? base.substring(1) : base;
        ArrayList<String> baseArray = new ArrayList<>();
        baseArray.add(base);
        return startsWith(baseArray);
    }
    public boolean startsWith(List<String> baseArray) {
        if (baseArray.isEmpty() && !(this.path.isEmpty())) {
            return false;
        }
        if (baseArray.size() > this.path.size()) {
            return false;
        }
        for (int i = 0; i< baseArray.size(); i++) {
            String thisBase = this.path.get(i);
            thisBase = thisBase.startsWith("$")? thisBase.substring(1) : thisBase;
            String otherBase = baseArray.get(i);
            otherBase = otherBase.startsWith("$")? otherBase.substring(1) : otherBase;
            if (!thisBase.equals(otherBase)) {
                return false;
            }
        }
        return true;
    }


    public boolean startsWith(AccessPath base) {
        if (base == null) {
            return false;
        }
        return this.startsWith(base.path);
    }



    public boolean hasCommonPrefix(AccessPath other){
        if (other == null) {
            return false;
        }
        return this.pathEquals(other) || this.isPrefixOf(other) || other.isPrefixOf(this);
    }


    public boolean isPrefixOf(AccessPath base) {
        if (base == null) {
            return false;
        }
        if (this.isEmpty() || base.isEmpty()) {
            return false;
        }
        if (this.path.size() > base.path.size()-1) {
            return false;
        }
        for (int i = 0; i< this.path.size(); i++) {
            String thisBase = this.path.get(i);
            thisBase = thisBase.startsWith("$")? thisBase.substring(1) : thisBase;
            String otherBase = base.path.get(i);
            otherBase = otherBase.startsWith("$")? otherBase.substring(1) : otherBase;
            if (!thisBase.equals(otherBase)) {
                return false;
            }
        }
        return true;
    }

    public AccessPath add(String p, Type type, StatementInstance si){
        if (this.path.isEmpty()) {
            throw new EmptyAccessPathException();
        }
        if (!p.equals("")) {
            this.path.add(p);
            this.classPath.add(type);
        }
        clipAccessPath();
        setStatementInstance(si);
        return this;
    }

    public AccessPath add(Pair<ArrayList<String>, ArrayList<Type>> other, StatementInstance si) {
        return add(other.getO1(), other.getO2(), si);
    }

    public AccessPath add(List<String> p, List<Type> types, StatementInstance si){
        if (this.path.isEmpty()) {
            throw new EmptyAccessPathException();
        }
        if ((!p.isEmpty()) && AnalysisUtils.noCommons(p, this.path, types, this.classPath)) {
            this.path.addAll(p);
            this.classPath.addAll(types);
        }
        clipAccessPath();
        setStatementInstance(si);
        return this;
    }

    private void clipAccessPath() {
        if (this.path.size() > Constants.ACCESS_PATH_LENGTH) {
            this.clipped = true;
            this.path = new ArrayList<>(this.path.subList(0, Constants.ACCESS_PATH_LENGTH));
            this.classPath = new ArrayList<>(this.classPath.subList(0, Constants.ACCESS_PATH_LENGTH));
        }
    }

    public Pair<String, Type> getBase(){
        return new Pair<>(path.get(0), classPath.get(0));
    }

    public List<String> getFields(){
        ArrayList<String> fields = new ArrayList<>();
        try {
            fields = new ArrayList<>(path.subList(1, path.size()));
        } catch (ArrayIndexOutOfBoundsException e) {
            // pass
        }
        return fields;
    }
    public List<Type> getFieldsTypes(){
        return new ArrayList<>(this.classPath.subList(1, this.classPath.size()));

    }

    public String getField(){
        if (this.path.size() > 1) {
            return this.path.get(this.path.size()-1);
        } else {
            return "";
        }
    }

    public String getFieldSubSignature(){
        if (this.path.size() > 1) {
            return this.classPath.get(this.classPath.size()-1) + " " + this.path.get(this.path.size()-1);
        } else {
            return "";
        }
    }


    public Pair<ArrayList<String>, ArrayList<Type>> getAfter(AccessPath other){
        return getAfter(other.getPath());
    }

    public boolean hasAfter(AccessPath other) {
        return !getAfter(other).getO1().isEmpty();
    }

    public Pair<ArrayList<String>, ArrayList<Type>> getAfter(List<String> base){
        Pair<ArrayList<String>, ArrayList<Type>> ret = new Pair<>();
        ret.setO1(new ArrayList<>());
        ret.setO2(new ArrayList<>());
        if (this.startsWith(base)) {
            try {
                ret.setO1(new ArrayList<>(path.subList(base.size(), path.size())));
                ret.setO2(new ArrayList<>(classPath.subList(base.size(), classPath.size())));
            } catch (IllegalArgumentException e) {
                // Ignored
            }
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AccessPath)) {
            return false;
        }
        AccessPath other = (AccessPath) o;
        
        return path.equals(other.path) &&
               classPath.equals(other.classPath) && 
               (usedAt == other.usedAt) &&
               (definedAt == other.definedAt);
    }

    public boolean pathEquals(AccessPath other) {
        return this.path.equals(other.path);
    }

    public boolean baseEquals(String otherBase){
        String thisBase = getBase().getO1().startsWith("$")? getBase().getO1().substring(1): getBase().getO1();
        otherBase = otherBase.startsWith("$")? otherBase.substring(1): otherBase;
        return thisBase.equals(otherBase);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + path.hashCode();
        result = 31 * result + classPath.hashCode();
        result = 31 * result + usedAt;
        result = 31 * result + definedAt;
        return result;
    }

    @Override
    public String toString() {
        return "(" + getPathString() + ":" + getUsedLine() + ":" + getDefinedLine() + ")";
    }

    public boolean isEmpty(){
        return path.isEmpty();
    }

    public int classIndex(Type type) {
        return classPath.indexOf(type);
    }

    public AccessPath clipFromIndex(int classIndex, int usedAt, int definedAt, StatementInstance si) {
        AccessPath other = new AccessPath(usedAt, definedAt, si);
        other.isStaticField = this.isStaticField;
        other.clipped = this.clipped;
        other.path.addAll(this.path.subList(classIndex, this.path.size()));
        other.classPath.addAll(this.classPath.subList(classIndex, this.path.size()));
        return other;
    }

    public void replaceBase(String e) {
        this.path.set(0, e);
    }

    public AccessPath extendFields(AccessPath other, AccessPath base, int usedAt, int definedAt, StatementInstance si) {
        if (other == null || other.isEmpty()) {
            return new AccessPath(usedAt, definedAt, si);
        }
        AccessPath extended = new AccessPath(other, usedAt, definedAt, si).add(this.getAfter(base), si);
        extended.setAddedSide(other);
        return extended;
    }

    public boolean isParameter(StatementInstance si) {
        if (this.isStaticField) {
            return true;
        }
        SootMethod method = si.getMethod();
        int maxParamIndex = method.getParameterCount() + (method.isStatic()? 0:1);
        Matcher matcher = Pattern.compile("\\d+").matcher(this.getBase().getO1());
        matcher.find();
        int variableIndex = Integer.parseInt(matcher.group());
        return (variableIndex < maxParamIndex) || isStaticField;
    }

    public boolean addedSideIsPrefixOf(AccessPath lhs) {
        if (addedSide == null) {
            return false;
        }
        return addedSide.isPrefixOf(lhs);
    }
}