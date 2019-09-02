package at.forsyte.apalache.tla.bmcmt.config

import at.forsyte.apalache.infra.passes._
import at.forsyte.apalache.tla.assignments.passes.{AssignmentPass, AssignmentPassImpl}
import at.forsyte.apalache.tla.bmcmt.analyses._
import at.forsyte.apalache.tla.bmcmt.passes._
import at.forsyte.apalache.tla.bmcmt.types.eager.TrivialTypeFinder
import at.forsyte.apalache.tla.bmcmt.types.{CellT, TypeFinder}
import at.forsyte.apalache.tla.imp.passes.{SanyParserPass, SanyParserPassImpl}
import at.forsyte.apalache.tla.lir.storage.ChangeListener
import at.forsyte.apalache.tla.lir.transformations.{TransformationListener, TransformationTracker}
import at.forsyte.apalache.tla.pp.passes.{PreproPass, PreproPassImpl}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}

/**
  * A configuration that binds all the passes from the parser to the checker.
  * If you are not sure how the binding works, check the tutorial on Google Guice.
  *
  * @author Igor Konnov
  */
class CheckerModule extends AbstractModule {
  override def configure(): Unit = {
    // the options singleton
    bind(classOf[PassOptions])
      .to(classOf[WriteablePassOptions])

    // stores
    bind(classOf[FreeExistentialsStore])
      .to(classOf[FreeExistentialsStoreImpl])
    bind(classOf[FormulaHintsStore])
      .to(classOf[FormulaHintsStoreImpl])
    bind(classOf[ExprGradeStore])
      .to(classOf[ExprGradeStoreImpl])
    bind(new TypeLiteral[TypeFinder[CellT]] {})
      .to(classOf[TrivialTypeFinder])   // using a trivial type finder

    // transformation tracking
    // TODO: the binding of TransformationListener should disappear in the future
    bind(classOf[TransformationListener])
      .to(classOf[ChangeListener])
    // check TransformationTrackerProvider to find out which listeners the tracker is using
    bind(classOf[TransformationTracker])
        .toProvider(classOf[TransformationTrackerProvider])

    // SanyParserPassImpl is the default implementation of SanyParserPass
    bind(classOf[SanyParserPass])
      .to(classOf[SanyParserPassImpl])
    // and it also the initial pass for PassChainExecutor
    bind(classOf[Pass])
      .annotatedWith(Names.named("InitialPass"))
      .to(classOf[SanyParserPass])
    // the next pass after SanyParserPass is PreproPass
    bind(classOf[PreproPass])
      .to(classOf[PreproPassImpl])
    bind(classOf[Pass])
      .annotatedWith(Names.named("AfterParser"))
      .to(classOf[PreproPass])
    // the next pass after PreproPass is AssignmentPass
    bind(classOf[AssignmentPass])
      .to(classOf[AssignmentPassImpl])
    bind(classOf[Pass])
      .annotatedWith(Names.named("AfterPrepro"))
      .to(classOf[AssignmentPass])
    // the next pass after AssignmentPass is GradePass
    bind(classOf[GradePass])
      .to(classOf[GradePassImpl])
    bind(classOf[Pass])
      .annotatedWith(Names.named("AfterAssignment"))
      .to(classOf[GradePass])
    // the next pass after GradePass is SimpleSkolemizationPass
    bind(classOf[HintsAndSkolemizationPass])
      .to(classOf[HintsAndSkolemizationPassImpl])
    bind(classOf[Pass])
      .annotatedWith(Names.named("AfterGrade"))
      .to(classOf[HintsAndSkolemizationPass])
    // the next pass after SimpleSkolemizationPass is BoundedCheckerPass
    bind(classOf[BoundedCheckerPass])
      .to(classOf[BoundedCheckerPassImpl])
    bind(classOf[Pass])
      .annotatedWith(Names.named("AfterSkolem"))
      .to(classOf[BoundedCheckerPass])
    // the final pass is TerminalPass
    bind(classOf[Pass])
      .annotatedWith(Names.named("AfterChecker"))
      .to(classOf[TerminalPass])
  }

}