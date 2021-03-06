package io.gitsocratic.command.question

import groovy.transform.Canonical

/**
 * Represents a user-input value in a source code question.
 *
 * @version 0.2.1
 * @since 0.1
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
@Canonical
class QuestionValue {

    String variable
    QuestionValueConverter valueConverter

    QuestionValue(String variable, QuestionValueConverter valueConverter) {
        this.variable = variable
        this.valueConverter = valueConverter
    }
}
