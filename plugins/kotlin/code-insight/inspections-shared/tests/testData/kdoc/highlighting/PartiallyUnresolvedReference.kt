// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * In [A.<warning descr="Cannot resolve symbol 'bar'">bar</warning>], A.bar should be marked as unresolved, but A should not.
 */
fun foo(){}

class A {}

// NO_CHECK_INFOS