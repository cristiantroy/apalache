package at.forsyte.apalache.tla.pp

import at.forsyte.apalache.tla.lir._
import at.forsyte.apalache.tla.lir.oper._
import at.forsyte.apalache.tla.lir.values.{TlaBool, TlaInt, TlaStr}

import scala.math.BigInt

/**
 * <p>A base class for constant simplification that is shared by more specialized simplifiers.</p>
 *
 * <p>Bugfix #450: make sure that the integers are simplified with BigInt.</p>
 *
 * @author Igor Konnov
 */
abstract class ConstSimplifierBase {
  private val boolTag = Typed(BoolT1())
  private val intTag = Typed(IntT1())

  /**
   * A shallow simplification that does not recurse into the expression structure.
   */
  def simplifyShallow: TlaEx => TlaEx = {
    // !FALSE = TRUE
    // !TRUE = FALSE
    case OperEx(TlaBoolOper.not, ValEx(TlaBool(b))) => ValEx(TlaBool(!b))(boolTag)

    // !!x = x
    case OperEx(TlaBoolOper.not, OperEx(TlaBoolOper.not, underDoubleNegation)) => underDoubleNegation

    // Relace \neq with \eq
    // x /= y = !(x = y)
    case OperEx(TlaOper.ne, lhs, rhs) =>
      val equality = simplifyShallow(OperEx(TlaOper.eq, lhs, rhs)(boolTag))
      simplifyShallow(OperEx(TlaBoolOper.not, equality)(boolTag))
    // !(x /= y) = x = y
    case OperEx(TlaBoolOper.not, OperEx(TlaOper.ne, lhs, rhs)) => simplifyShallow(OperEx(TlaOper.eq, lhs, rhs)(boolTag))

    // Replace \notin with \in
    // x \notin y = !(x \in y)
    case OperEx(TlaSetOper.notin, lhs, rhs) =>
      OperEx(TlaBoolOper.not, OperEx(TlaSetOper.in, lhs, rhs)(boolTag))(boolTag)
    // !(x \notin y) = x \in y
    case OperEx(TlaBoolOper.not, OperEx(TlaSetOper.notin, lhs, rhs)) => OperEx(TlaSetOper.in, lhs, rhs)(boolTag)

    // integer operations
    // Evaluate constant addition
    case OperEx(TlaArithOper.plus, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left + right))(intTag)
    // 0 + x = x
    case OperEx(TlaArithOper.plus, ValEx(TlaInt(left)), rightEx) if left == 0 => rightEx
    // x + 0 = x
    case OperEx(TlaArithOper.plus, leftEx, ValEx(TlaInt(right))) if right == 0 => leftEx

    // Evaluate constant subtraction
    case OperEx(TlaArithOper.minus, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left - right))(intTag)
    // 0 - x = -x
    case OperEx(TlaArithOper.minus, ValEx(TlaInt(left)), rightEx) if left == 0 =>
      OperEx(TlaArithOper.uminus, rightEx)(intTag)
    // x - 0 = x
    case OperEx(TlaArithOper.minus, leftEx, ValEx(TlaInt(right))) if right == 0 => leftEx
    // x - x = 0 (this actually happens)
    case OperEx(TlaArithOper.minus, leftEx, rightEx) if (leftEx == rightEx) => ValEx(TlaInt(0))(intTag)

    // Evaluate constant multiplication
    case OperEx(TlaArithOper.mult, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left * right))(intTag)
    // 0 * x = 0
    case OperEx(TlaArithOper.mult, ValEx(TlaInt(left)), rightEx) if (left == 0) => ValEx(TlaInt(0))(intTag)
    // 1 * x = x
    case OperEx(TlaArithOper.mult, ValEx(TlaInt(left)), rightEx) if (left == 1) => rightEx
    // x * 0 = 0
    case OperEx(TlaArithOper.mult, leftEx, ValEx(TlaInt(right))) if (right == 0) => ValEx(TlaInt(0))(intTag)
    // x * 1 = x
    case OperEx(TlaArithOper.mult, leftEx, ValEx(TlaInt(right))) if (right == 1) => leftEx

    // x / 0 = undefined
    case ex @ OperEx(TlaArithOper.div, leftEx, ValEx(TlaInt(right))) if (right == 0) =>
      throw new TlaInputError(s"Division by zero at ${ex.toString}")
    // Evaluate constant division
    case OperEx(TlaArithOper.div, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left / right))(intTag)
    // 0 / x = 0
    case OperEx(TlaArithOper.div, ValEx(TlaInt(left)), rightEx) if (left == 0) => ValEx(TlaInt(0))(intTag)
    // x / 1 = x
    case OperEx(TlaArithOper.div, leftEx, ValEx(TlaInt(right))) if (right == 1) => leftEx
    // x / x = 1
    case OperEx(TlaArithOper.div, leftEx, rightEx) if (leftEx == rightEx) => ValEx(TlaInt(1))(intTag)

    // x % 0 = undefined
    case ex @ OperEx(TlaArithOper.mod, leftEx, ValEx(TlaInt(right))) if (right == 0) =>
      throw new TlaInputError(s"Mod by zero at ${ex.toString}")
    // Evaluate constant mod
    case OperEx(TlaArithOper.mod, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaInt(left % right))(intTag)
    // x % 1 = 0
    case OperEx(TlaArithOper.mod, leftEx, ValEx(TlaInt(right))) if (right == 1) => ValEx(TlaInt(0))(intTag)
    // x % x = 0
    case OperEx(TlaArithOper.mod, leftEx, rightEx) if (leftEx == rightEx) => ValEx(TlaInt(0))(intTag)

    // 0 ^ 0 = undefined
    case ex @ OperEx(TlaArithOper.exp, ValEx(TlaInt(base)), ValEx(TlaInt(power))) if (base == 0 && power == 0) =>
      throw new TlaInputError(s"0 ^ 0 is undefined")
    // Try to evaluante constant exponentiation
    case ex @ OperEx(TlaArithOper.exp, ValEx(TlaInt(base)), ValEx(TlaInt(power))) =>
      if (power < 0) {
        throw new TlaInputError(s"Negative power at ${ex.toString}")
      } else if (!power.isValidInt) {
        throw new TlaInputError(
            s"Power of ${power} is bigger than the max allowed of ${Int.MaxValue} at ${ex.toString}")
      } else {
        try {
          // This can take a long time for big base values i.e. 2147484647 ^ 1100000
          // Maybe we should consider implementing a timeout
          ValEx(TlaInt(base.pow(power.toInt)))(intTag)
        } catch {
          case _: ArithmeticException =>
            throw new TlaInputError(s"The result of ${ex.toString} exceedes the limit of 2^${Int.MaxValue}")
        }
      }
    // x ^ 0 = 1
    case OperEx(TlaArithOper.exp, leftEx, ValEx(TlaInt(right))) if (right == 0) => ValEx(TlaInt(1))(intTag)
    // x ^ 1 = x
    case OperEx(TlaArithOper.exp, leftEx, ValEx(TlaInt(right))) if (right == 1) => leftEx
    // 0 ^ x = 0
    case OperEx(TlaArithOper.exp, ValEx(TlaInt(left)), rightEx) if (left == 0) => ValEx(TlaInt(0))(intTag)
    // 1 ^ x = 1
    case OperEx(TlaArithOper.exp, ValEx(TlaInt(left)), rightEx) if (left == 1) => ValEx(TlaInt(1))(intTag)

    // -0 = 0
    case OperEx(TlaArithOper.uminus, ValEx(TlaInt(value))) if (value == 0) => ValEx(TlaInt(0))(intTag)
    // Evaluate unary minus
    case OperEx(TlaArithOper.uminus, ValEx(TlaInt(value))) => ValEx(TlaInt(-value))(intTag)

    // Evaluate relational expressions between integers
    case OperEx(TlaArithOper.lt, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaBool(left < right))(boolTag)
    case OperEx(TlaArithOper.le, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaBool(left <= right))(boolTag)
    case OperEx(TlaArithOper.gt, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaBool(left > right))(boolTag)
    case OperEx(TlaArithOper.ge, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaBool(left >= right))(boolTag)

    // x == x = TRUE
    case OperEx(TlaOper.eq, left, right) if (left == right) => ValEx(TlaBool(true))(boolTag)

    // Evaluate constant comparisson
    case OperEx(TlaOper.eq, ValEx(TlaInt(left)), ValEx(TlaInt(right))) => ValEx(TlaBool(left == right))(boolTag)
    // bugfix #197
    case OperEx(TlaOper.eq, ValEx(TlaStr(left)), ValEx(TlaStr(right))) => ValEx(TlaBool(left == right))(boolTag)

    // boolean operations
    case OperEx(TlaBoolOper.and, args @ _*) =>
      val simpArgs = args.filterNot {
        _ == ValEx(TlaBool(true))(boolTag)
      }
      simpArgs match {
        case Seq()      => ValEx(TlaBool(true))(boolTag) // an empty conjunction is true
        case Seq(first) => first
        // one false make conjunction false
        case _ if simpArgs.contains(ValEx(TlaBool(false))(boolTag)) => ValEx(TlaBool(false))(boolTag)
        // TRUE /\ x /\ y = x /\ y
        case _ => OperEx(TlaBoolOper.and, simpArgs: _*)(boolTag)
      }

    case OperEx(TlaBoolOper.or, args @ _*) =>
      val simpArgs = args.filterNot {
        _ == ValEx(TlaBool(false))(boolTag)
      }
      simpArgs match {
        case Seq()      => ValEx(TlaBool(false))(boolTag) // an empty disjunction is false
        case Seq(first) => first
        // one true make disjunction true
        case _ if simpArgs.contains(ValEx(TlaBool(true))(boolTag)) => ValEx(TlaBool(true))(boolTag)
        // FALSE \/ x \/ y = x \/ y
        case _ => OperEx(TlaBoolOper.or, simpArgs: _*)(boolTag)
      }

    // Evaluate implication of constants
    case OperEx(TlaBoolOper.implies, ValEx(TlaBool(left)), ValEx(TlaBool(right))) =>
      ValEx(TlaBool(!left || right))(boolTag)
    case OperEx(TlaBoolOper.implies, ValEx(TlaBool(false)), _) => ValEx(TlaBool(true))(boolTag)
    case OperEx(TlaBoolOper.implies, _, ValEx(TlaBool(true)))  => ValEx(TlaBool(true))(boolTag)

    // TRUE -> x = x
    case OperEx(TlaBoolOper.implies, ValEx(TlaBool(true)), right) => right
    // x -> FALSE = !x
    case OperEx(TlaBoolOper.implies, lhs, ValEx(TlaBool(false))) =>
      simplifyShallow(OperEx(TlaBoolOper.not, lhs)(boolTag))

    // Evaluate equivalence between constants
    case OperEx(TlaBoolOper.equiv, ValEx(TlaBool(left)), ValEx(TlaBool(right))) =>
      ValEx(TlaBool(left == right))(boolTag)

    // TRUE <=> x = x
    case OperEx(TlaBoolOper.equiv, ValEx(TlaBool(true)), right) => right
    // FALSE <=> x = !x
    case OperEx(TlaBoolOper.equiv, ValEx(TlaBool(false)), right) =>
      simplifyShallow(OperEx(TlaBoolOper.not, right)(boolTag))
    // x <=> TRUE = x
    case OperEx(TlaBoolOper.equiv, left, ValEx(TlaBool(true))) => left
    // x <=> FALSE = !x
    case OperEx(TlaBoolOper.equiv, left, ValEx(TlaBool(false))) =>
      simplifyShallow(OperEx(TlaBoolOper.not, left)(boolTag))

    // many ite expressions can be simplified like this
    // IF true THEN x ELSE y = x
    case OperEx(TlaControlOper.ifThenElse, ValEx(TlaBool(true)), thenEx, _) => thenEx
    // IF false THEN x ELSE y = y
    case OperEx(TlaControlOper.ifThenElse, ValEx(TlaBool(false)), _, elseEx) => elseEx
    // IF x THEN TRUE ELSE FALSE = x
    case OperEx(TlaControlOper.ifThenElse, pred, ValEx(TlaBool(true)), ValEx(TlaBool(false))) => pred
    // IF x THEN FALSE ELSE TRUE = !x
    case OperEx(TlaControlOper.ifThenElse, pred, ValEx(TlaBool(false)), ValEx(TlaBool(true))) =>
      simplifyShallow(OperEx(TlaBoolOper.not, pred)(boolTag))
    // IF x THEN FALSE ELSE y = !x /\ y
    case OperEx(TlaControlOper.ifThenElse, pred, ValEx(TlaBool(false)), elseEx) =>
      val newPredicate = simplifyShallow(OperEx(TlaBoolOper.not, pred)(boolTag))
      simplifyShallow(OperEx(TlaBoolOper.and, newPredicate, elseEx)(boolTag))
    // IF x THEN y ELSE y = y
    case OperEx(TlaControlOper.ifThenElse, _, thenEx, elseEx) if (thenEx == elseEx) => thenEx

    // default
    case ex =>
      ex
  }
}
