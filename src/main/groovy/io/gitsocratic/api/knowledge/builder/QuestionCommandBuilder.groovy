package io.gitsocratic.api.knowledge.builder

import io.gitsocratic.command.impl.Question
import io.gitsocratic.command.question.SourceQuestion

/**
 * Used to construct the 'question' command via API.
 *
 * @version 0.2.1
 * @since 0.2
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
class QuestionCommandBuilder {

    private SourceQuestion question

    QuestionCommandBuilder question(String question) {
        if (question.endsWith("?")) {
            question = question.substring(0, question.length() - 1)
        }
        this.question = SourceQuestion.toSourceQuestion(question)
        return this
    }

    QuestionCommandBuilder question(SourceQuestion question) {
        this.question = question
        return this
    }

    Question build() {
        return new Question(question)
    }
}
