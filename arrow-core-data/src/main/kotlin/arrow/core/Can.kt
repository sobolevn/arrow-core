package arrow.core

import arrow.higherkind
import arrow.typeclasses.Semigroup
import arrow.typeclasses.Show

/**
 * ank_macro_hierarchy(arrow.core.Can)
 *
 *
 * Implementation of Haskell's [Can](https://hackage.haskell.org/package/smash-0.1.1.0/docs/Data-Can.html)
 *
 * Represents a right-biased disjunction of either [A], [B], both [A] and [B] or none of them.
 *
 * This can be represented mathematically as the product of two components that are optional:
 *
 * ```
 * (1 + a) * (1 + b)   // (1 + a) is the union of an empty case and a base case aka [Option]
 * ~ 1 + a + b + a*b   // This is expressed as 4 permutations: None, Left, Right, or Both
 * ~ Option (Ior a b)  // Ior (or There in Haskell) can be defined as `a + b + a*b`, therefore wrapping it with Option adds the empty case
 * ~ Can a b           // And that's how we get to [Can]
 * ```
 * It can be easier to visualize in a picture:
 *
 * ```
 * Can:
 *          A (Left)
 *          |
 * None +---+---+ A and B (Both)
 *          |
 *          B (Right)
 * ```
 *
 * An instance of [Can]<`A`,`B`> is one of:
 *  - [Can.None]
 *  - [Can.Left] <`A`>
 *  - [Can.Right] <`B`>
 *  - [Can.Both]<`A`,`B`>
 *
 * Similarly to [Ior], [Can] differs from [Either] in that it can contain both [A] and [B]. On top of that it can contain neither of them.
 * This means that it's isomorphic to using [Option]<[Ior]<`A`, `B`>>.
 *
 * Operations available are biased towards [B].
 *
 * Implementation Note: The names of [Left] and [Right] were used instead of the original `One` and `Eno` to match other data classes like [Either] or [Ior]
 *
 */
@higherkind
sealed class Can<out A, out B>(
  /**
   * Returns true if the option is [Can.None], false otherwise.
   * @note Used only for performance instead of fold.
   *
   * Example:
   * ```
   * Can.None.isEmpty                      // Result: true
   * Can.Left("tulip").isEmpty             // Result: false
   * Can.Right("venus fly-trap").isEmpty   // Result: false
   * Can.Both("venus", "fly-trap").isEmpty // Result: false
   * ```
   */
  val isEmpty: Boolean = false,
  /**
   * `true` if this is a [Can.Right], `false` otherwise.
   * @note Used only for performance instead of fold.
   *
   * Example:
   * ```
   * Can.None.isRight                      // Result: false
   * Can.Left("tulip").isRight             // Result: false
   * Can.Right("venus fly-trap").isRight   // Result: true
   * Can.Both("venus", "fly-trap").isRight // Result: false
   * ```
   */
  val isLeft: Boolean = false,
  /**
   * `true` if this is a [Can.Left], `false` otherwise.
   * @note Used only for performance instead of fold.
   *
   * Example:
   * ```
   * Can.None.isLeft                      // Result: false
   * Can.Left("tulip").isLeft             // Result: true
   * Can.Right("venus fly-trap").isLeft   // Result: false
   * Can.Both("venus", "fly-trap").isLeft // Result: false
   * ```
   */
  val isRight: Boolean = false,
  /**
   * `true` if this is a [Can.Both], `false` otherwise.
   * @note Used only for performance instead of fold.
   *
   * Example:
   * ```
   * Can.None.isBoth                      // Result: false
   * Can.Left("tulip").isBoth             // Result: false
   * Can.Right("venus fly-trap").isBoth   // Result: false
   * Can.Both("venus", "fly-trap").isBoth // Result: true
   * ```
   */
  val isBoth: Boolean = false
) : CanOf<A, B> {

  companion object {

    /**
     * Create an [Ior] from two Options if at least one of them is defined.
     *
     * @param oa an element (optional) for the left side of the [Ior]
     * @param ob an element (optional) for the right side of the [Ior]
     *
     * @return [None] if both [oa] and [ob] are [None]. Otherwise [Some] wrapping
     * an [Ior.Left], [Ior.Right], or [Ior.Both] if [oa], [ob], or both are defined (respectively).
     */
    fun <A, B> fromOptions(oa: Option<A>, ob: Option<B>): Can<A, B> = fromNullables(oa.orNull(), ob.orNull())

    /**
     * The same as [fromOptions] but with nullable inputs.
     */
    fun <A, B> fromNullables(a: A?, b: B?): Can<A, B> = when {
      a != null && b != null -> Both(a, b)
      b != null -> Right(b)
      a != null -> Left(a)
      else -> None
    }

    fun none(): Can<Nothing, Nothing> = None
    fun <A> left(left: A): Can<A, Nothing> = Left(left)
    fun <B> right(right: B): Can<Nothing, B> = Right(right)
    fun <A, B> both(left: A, right: B): Can<A, B> = Both(left, right)

    private tailrec fun <A2, A, B> Semigroup<A2>.loop(v: Can<A2, Either<A, B>>, f: (A) -> CanOf<A2, Either<A, B>>): Can<A2, B> = when (v) {
      is None -> None
      is Left -> Left(v.a)
      is Right -> when (val either = v.b) {
        is Either.Right -> Right(either.b)
        is Either.Left -> loop(f(either.a).fix(), f)
      }
      is Both -> when (val either = v.b) {
        is Either.Right -> Both(v.a, either.b)
        is Either.Left -> when (val fnb = f(either.a).fix()) {
          is None -> None
          is Left -> Left(v.a.combine(fnb.a))
          is Right -> loop(Both(v.a, fnb.b), f)
          is Both -> loop(Both(v.a.combine(fnb.a), fnb.b), f)
        }
      }
    }

    fun <A2, A, B> tailRecM(a: A, f: (A) -> CanOf<A2, Either<A, B>>, SL: Semigroup<A2>): Can<A2, B> =
      SL.run { loop(f(a).fix(), f) }
  }

  /**
   * Transforms the right side from [B] to [C].
   *
   * This has no effect if this is a [Left] or [None].
   * In the instance that we have a [Right] instance it will apply [f].
   * Equally, with [Both], `b` will be transformed, but `a` will remain the same.
   *
   * @param f the function to apply
   * @see flatMap
   */
  fun <C> map(f: (B) -> C): Can<A, C> =
    fold({ None }, { Left(it) }, { Right(f(it)) }, { a, b -> Both(a, f(b)) })

  /**
   * The given function is applied if this is a [Left] or [Both] to `A`.
   *
   * Example:
   * ```
   * None.map { "flower" }                   // Result: None
   * Right(12).map { "flower" }              // Result: Right(12)
   * Left(12).map { "flower" }               // Result: Left("power")
   * Both(12, "power").map { "flower $it" }  // Result: Both("flower 12", "power")
   * ```
   */
  fun <C> mapLeft(fa: (A) -> C): Can<C, B> =
    fold({ None }, { Left(fa(it)) }, { Right((it)) }, { a, b -> Both(fa(a), b) })

  fun <C, D> bimap(fa: (A) -> C, fb: (B) -> D): Can<C, D> =
    fix().fold({ None }, { Left(fa(it)) }, { Right(fb(it)) }, { l, r -> Both(fa(l), fb(r)) })

  /**
   * Transforms this into an instance of [C] depending on the case.
   */
  fun <C> fold(
    ifNone: () -> C,
    ifLeft: (A) -> C,
    ifRight: (B) -> C,
    ifBoth: (A, B) -> C
  ): C = when (this) {
    is None -> ifNone()
    is Left -> ifLeft(a)
    is Right -> ifRight(b)
    is Both -> ifBoth(a, b)
  }

  /**
   * Similar to [map] but results in a [None] if the result fo [f] is null.
   */
  fun <C> mapNotNull(f: (B) -> C?): Can<A, C> =
    fromOptions(left(), right().mapNotNull(f))

  /**
   * Returns this if the predicate over [B] passes, or [None] otherwise
   *
   * @param predicate the predicate used for testing.
   */
  fun filter(predicate: Predicate<B>): Can<A, B> =
    fromOptions(left(), right().filter(predicate))

  /**
   * Opposite of [filter]
   *
   * @param predicate the predicate used for testing.
   */
  fun filterNot(predicate: Predicate<B>): Can<A, B> =
    fromOptions(left(), right().filterNot(predicate))

  /**
   * Returns this if the predicate over [A] passes, or [None] otherwise
   *
   * @param predicate the predicate used for testing.
   */
  fun filterLeft(predicate: Predicate<A>): Can<A, B> =
    fromOptions(left().filter(predicate), right())

  /**
   * Opposite of [filterLeft]
   *
   * @param predicate the predicate used for testing.
   */
  fun filterNotLeft(predicate: Predicate<A>): Can<A, B> =
    fromOptions(left().filterNot(predicate), right())

  /**
   * Returns true if [B] passes the provided predicate for [Right], or [Both] instances.
   *
   * @param predicate the predicate used for testing.
   */
  fun exists(predicate: Predicate<B>): Boolean =
    fold({ false }, { false }, { predicate(it) }, { _, b -> predicate(b) })

  fun <C> foldLeft(c: C, f: (C, B) -> C): C =
    fold({ c }, { c }, { f(c, it) }, { _, b -> f(c, b) })

  fun <C> foldRight(lc: Eval<C>, f: (B, Eval<C>) -> Eval<C>): Eval<C> =
    fold({ lc }, { lc }, { Eval.defer { f(it, lc) } }, { _, b -> Eval.defer { f(b, lc) } })

  fun <C> bifoldLeft(c: C, f: (C, A) -> C, g: (C, B) -> C): C =
    fold({ c }, { f(c, it) }, { g(c, it) }, { a, b -> g(f(c, a), b) })

  fun <C> bifoldRight(c: Eval<C>, f: (A, Eval<C>) -> Eval<C>, g: (B, Eval<C>) -> Eval<C>): Eval<C> =
    fold({ c }, { f(it, c) }, { g(it, c) }, { a, b -> f(a, g(b, c)) })

  /**
   * Return the isomorphic [Option]<[Ior]> of this [Can]
   */
  fun unwrap(): Option<Ior<A, B>> =
    fold({ Option.empty() }, { Some(Ior.Left(it)) }, { Some(Ior.Right(it)) }, { a, b -> Some(Ior.Both(a, b)) })

  /**
   * Inverts the components:
   *  - If [None] is remains [None]
   *  - If [Left]<`A`> it returns [Right]<`A`>
   *  - If [Right]<`B`> it returns [Left]<`B`>
   *  - If [Both]<`A`, `B`> it returns [Both]<`B`, `A`>
   *
   * Example:
   * ```
   * Can.None.swap()                  // Result: None
   * Can.Left("left").swap()          // Result: Right("left")
   * Can.Right("right").swap()        // Result: Left("right")
   * Can.Both("left", "right").swap() // Result: Both("right", "left")
   * ```
   */
  fun swap(): Can<B, A> = fold({ None }, { Right(it) }, { Left(it) }, { a, b -> Both(b, a) })

  /**
   * Return this [Can] as [Pair] of [Option]
   *
   * Example:
   * ```
   * Can.None.pad()               // Result: Pair(None, None)
   * Can.Right(12).pad()          // Result: Pair(None, Some(12))
   * Can.Left(12).pad()           // Result: Pair(Some(12), None)
   * Can.Both("power", 12).pad()  // Result: Pair(Some("power"), Some(12))
   * ```
   */
  fun pad(): Pair<Option<A>, Option<B>> = fold(
    { Pair(Option.empty(), Option.empty()) },
    { Pair(Some(it), Option.empty()) },
    { Pair(Option.empty(), Some(it)) },
    { a, b -> Pair(Some(a), Some(b)) }
  )

  /**
   * Provides a printable description of [Can] given the relevant [Show] instances.
   */
  fun show(SL: Show<A>, SR: Show<B>): String = fold(
    { "None" },
    { "Left(${SL.run { it.show() }})" },
    { "Right(${SR.run { it.show() }})" },
    { a, b -> "Both(left=${SL.run { a.show() }}, right=${SR.run { b.show() }})" }
  )

  override fun toString(): String = show(Show.any(), Show.any())

  object None : Can<Nothing, Nothing>(isEmpty = true)
  data class Left<out A>(val a: A) : Can<A, Nothing>(isLeft = true)
  data class Right<out B>(val b: B) : Can<Nothing, B>(isRight = true)
  data class Both<out A, out B>(val a: A, val b: B) : Can<A, B>(isBoth = true)
}

/**
 * Returns a [Validated.Valid] containing the [Right] value or `B` if this is [Right] or [Both]
 * and [Validated.Invalid] if this is a [Left].
 *
 * Example:
 * ```
 * Can.None.toValidated { Invalid(-1) } // Result: Invalid(-1)
 * Can.Right(12).toValidated()          // Result: Valid(12)
 * Can.Left(12).toValidated()           // Result: Invalid(12)
 * Can.Both(12, "power").toValidated()  // Result: Valid("power")
 * ```
 * @param ifEmpty used to source an intance of [Validated]
 */
fun <A, B> CanOf<A, B>.toValidated(ifEmpty: () -> Validated<A, B>): Validated<A, B> =
  fix().fold(ifEmpty, ::Invalid, ::Valid, { _, b -> Valid(b) })

/**
 * Similar to [toValidated] but returning [None] if there is nothing to validate.
 *
 * Examples:
 * ```
 * Can.None.toValidated()               // Result: None
 * Can.Right(12).toValidated()          // Result: Some(Valid(12))
 * Can.Left(12).toValidated()           // Result: Some(Invalid(12))
 * Can.Both(12, "power").toValidated()  // Result: Some(Valid("power"))
 * ```
 * @return [None] if the [Can] is [Can.None], otherwise the result from [toValidated] inside [Some]
 */
fun <A, B> CanOf<A, B>.toValidated(): Option<Validated<A, B>> =
  fix().fold({ None }, { Option.just(Invalid(it)) }, { Option.just(Valid(it)) }) { _: A, b: B -> Option.just(Valid(b)) }

/**
 * Similar to [Can.unwrap] with a fallback alternative in case of working with an instance of [Can.None]
 */
fun <A, B> CanOf<A, B>.toIor(ifNone: () -> IorOf<A, B>): Ior<A, B> =
  fix().unwrap().getOrElse(ifNone).fix()

/**
 * Binds the given function across [Can.Right] or [Can.Both].
 *
 * @param f The function to bind across [Can.Right] or [Can.Both].
 */
fun <A, B, C> CanOf<A, B>.flatMap(SG: Semigroup<A>, f: (B) -> CanOf<A, C>): Can<A, C> =
  fix().fold({ Can.None }, { Can.Left(it) }, { f(it).fix() }, { a, b -> SG.flatMapCombine(a, b, f) })

private fun <A, B, C> Semigroup<A>.flatMapCombine(a: A, b: B, f: (B) -> CanOf<A, C>) =
  f(b).fix().fold({ Can.None }, { Can.Left(a.combine(it)) }, { Can.Both(a, it) }, { ll, rr -> Can.Both(a.combine(ll), rr) })

/**
 * Safe unwrapping of the right side.
 *
 * @return [B] if [Can] is [Right] or [Both], otherwise the result from [default].
 */
fun <A, B> CanOf<A, B>.getOrElse(default: () -> B): B =
  fix().fold({ default() }, { default() }, ::identity, { _, b -> b })

/**
 * Safe unwrapping of the left side.
 *
 * @return [A] if [Can] is [Left] or [Both], otherwise the result from [default].
 */
fun <A, B> CanOf<A, B>.getLeftOrElse(default: () -> A): A =
  fix().fold({ default() }, ::identity, { default() }, { a, _ -> a })

/**
 * Applies the provided "[Can]ned" function given the Semigroup definition provided by [SG]
 *
 * @return The result of applying this [Can] to the function encapsulated [ff]
 */
fun <A, B, C> CanOf<A, B>.ap(SG: Semigroup<A>, ff: CanOf<A, (B) -> C>): Can<A, C> =
  flatMap(SG) { a -> ff.fix().map { f: (B) -> C -> f(a) } }

/**
 * Converts the receiver [Pair]<`A`, `B`> into an instance of [Can].
 *
 * @return always an instance of [Can.Both]
 */
fun <A, B> Pair<A, B>.bothCan(): Can<A, B> = Can.Both(first, second)

/**
 * Converts the receiver [Tuple2]<`A`, `B`> into an instance of [Can].
 *
 * @return always an instance of [Can.Both]
 */
fun <A, B> Tuple2<A, B>.bothCan(): Can<A, B> = Can.Both(a, b)

/**
 * Converts a, potentially nullable, instance of [A] into an instance of [Can]
 *
 * @return [None] when `null` or [Can.Left]<`A`> otherwise
 */
fun <A> A?.leftCan(): Can<A, Nothing> =
  if (this == null) Can.None else Can.Left(this)

/**
 * Same as [Option.rightCan] but for nullable values
 */
fun <B> B?.rightCan(): Can<Nothing, B> =
  if (this == null) Can.None else Can.Right(this)

/**
 * Converts a instance of [Option]<[A]> into an instance of [Can]<[A]>
 *
 * @return [Can.None] when [None] or [Can.Right]<[A]> when [Some]<[A]>
 */
fun <A> OptionOf<A>.leftCan(): Can<A, Nothing> =
  fix().fold({ Can.None }, { a -> Can.Left(a) })

/**
 * Converts a instance of [Option]<[B]> into an instance of [Can]<[B]>
 *
 * @return [Can.None] when [None] or [Can.Right]<[B]> when [Some]<[B]>
 */
fun <B> OptionOf<B>.rightCan(): Can<Nothing, B> =
  fix().fold({ Can.None }, { b -> Can.Right(b) })

/**
 * Converts a given [Ior]<[A], [B]> instance into a [Can]<[A], [B]> instance.
 *
 * Mapping:
 * - Never       -> [Can.None]
 * - [Ior.Left]  -> [Can.Left]
 * - [Ior.Right] -> [Can.Right]
 * - [Ior.Both]  -> [Can.Both]
 *
 * @return Either [Can.Left], [Can.Right], or [Can.Both], never [Can.None]
 */
fun <A, B> IorOf<A, B>.toCan(): Can<A, B> =
  fix().fold({ Can.Left(it) }, { Can.Right(it) }) { a, b -> Can.Both(a, b) }

/**
 * Extraction of the left value, if present.
 *
 * Mapping:
 * - [Can.None] -> [None]
 * - [Can.Left] -> [Some]<`A`>
 * - [Can.Right] -> [None]
 * - [Can.Left] -> [Some]<`A`>
 *
 * @return [Some]<`A`> if we have [Left] or [Both], otherwise [None]
 */
fun <A, B> CanOf<A, B>.left(): Option<A> =
  fix().fold({ None }, { Option.just(it) }, { None }, { l, _ -> Option.just(l) })

/**
 * Deconstruction of the left side of this [Can]
 *
 * example:
 * ```
 * fun currentUserAndOrg() : Can<User, Org>
 *
 * val (user, org) = currentUserAndOrg()
 * ```
 */
operator fun <A, B> CanOf<A, B>.component1(): Option<A> = left()

/**
 * Extraction of the right value, if present.
 *
 * Mapping:
 * - [Can.None] -> [None]
 * - [Can.Left] -> [None]
 * - [Can.Right] -> [Some]<`A`>
 * - [Can.Left] -> [Some]<`A`>
 *
 * @return [Some]<`A`> if we have [Right] or [Both], otherwise [None]
 */
fun <A, B> CanOf<A, B>.right(): Option<B> =
  fix().fold({ None }, { None }, { Some(it) }, { _, r -> Some(r) })

/**
 * Deconstruction of the right side of this [Can]
 *
 * example:
 * ```
 * fun currentUserAndOrg() : Can<User, Org>
 *
 * val (user, org) = currentUserAndOrg()
 * ```
 */
operator fun <A, B> CanOf<A, B>.component2(): Option<B> = right()
