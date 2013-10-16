package com.jetbrains.python.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public class PyPsiFacadeImpl extends PyPsiFacade {
  private final Project myProject;

  public PyPsiFacadeImpl(Project project) {
    myProject = project;
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(String qNameString) {
    return new QualifiedNameResolverImpl(qNameString);
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(QualifiedName qualifiedName) {
    return new QualifiedNameResolverImpl(qualifiedName);
  }

  @Nullable
  @Override
  public PyClass findClass(String qName) {
    return PyClassNameIndex.findClass(qName, myProject);
  }

  @NotNull
  @Override
  public PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition) {
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  @Nullable
  @Override
  public PyType createUnionType(@NotNull Collection<PyType> members) {
    return PyUnionType.union(members);
  }

  @Nullable
  @Override
  public PyType createTupleType(@NotNull Collection<PyType> members, @NotNull PsiElement anchor) {
    return PyTupleType.create(anchor, members.toArray(new PyType[members.size()]));
  }

  @Nullable
  @Override
  public PyType parseTypeAnnotation(@NotNull String annotation, @NotNull PsiElement anchor) {
    return PyTypeParser.getTypeByName(anchor, annotation);
  }

  @Nullable
  @Override
  public String findShortestImportableName(@NotNull VirtualFile targetFile, @NotNull PsiElement anchor) {
    return QualifiedNameFinder.findShortestImportableName(anchor, targetFile);
  }
}
