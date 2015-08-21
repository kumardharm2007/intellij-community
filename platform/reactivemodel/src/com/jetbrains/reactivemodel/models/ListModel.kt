package com.jetbrains.reactivemodel.models

import com.github.krukow.clj_ds.PersistentVector
import com.github.krukow.clj_ds.Persistents
import com.github.krukow.clj_lang.IPersistentMap
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.util.emptyMeta

public data class ListModel(val list: PersistentVector<Model> = Persistents.vector()): AssocModel<Int, ListModel>, List<Model> by list {
    override val meta: IPersistentMap<String, *>
        get() = emptyMeta();
    override fun <T> acceptVisitor(visitor: ModelVisitor<T>): T = visitor.visitListModel(this)

    public constructor(vararg values: Model): this(Persistents.vector(*values))
    public constructor(l: List<Model>): this(Persistents.vector(l))

    override fun assoc(key: Int, value: Model?): ListModel = ListModel(list.plusN(key, value))
    override fun find(key: Int): Model? = list[key]

    public fun add(m: Model): ListModel = ListModel(list.plus(m))

    override fun patch(diff: Diff<Model>): Model {
        if(diff is ValueDiff<*>) { // new value is path
            assert(diff.newValue is ListModel)
            return diff.newValue as ListModel
        }

        if (diff !is ListDiff) {
            throw AssertionError()
        }

        if (diff.index == 0) {
            return ListModel(diff.nueu)
        }

        if (diff.index != list.size()) {
            throw UnsupportedOperationException("conflicts resolution not implemented yet, diff.index ${diff.index} list.size ${list.size()}")
        }

        return ListModel(list.plus(diff.nueu))
    }

    override fun diffImpl(other: Model): Diff<Model>? {
        if (other !is ListModel) {
            throw AssertionError()
        }
        if (!list.zip(other.list).all { pair ->  pair.first==pair.second}
                || other.list.size() < list. size()) { // shrinked
            return ListDiff(other.list, 0) // replace whole list
        }
        if (list.size() == other.list.size()) {
            return null
        }

        return ListDiff(other.list.subList(list.size(), other.list.size()), list.size())
    }
}

public data class ListDiff(val nueu: List<Model>, val index: Int): Diff<ListModel> {
    override fun <T> acceptVisitor(visitor: DiffVisitor<T>): T = visitor.visitListDiff(this)
}

public fun ListModel.toPath(): Path = map { (it as PrimitiveModel<*>).value }.drop(1).fold(Path(), { path, part -> path / part })
