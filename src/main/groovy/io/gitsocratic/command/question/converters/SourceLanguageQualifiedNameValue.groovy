package io.gitsocratic.command.question.converters

import com.codebrig.arthur.SourceLanguage
import io.gitsocratic.command.question.QuestionValueConverter

/**
 * Converts user-input value into SourceLanguage.qualifiedName.
 *
 * @version 0.2.1
 * @since 0.1
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
class SourceLanguageQualifiedNameValue implements QuestionValueConverter {
    @Override
    String convert(String value) {
        return SourceLanguage.valueOf(value.substring(0, 1).toUpperCase() + value.substring(1)).qualifiedName
    }
}
