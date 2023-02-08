package snyk.iac

object IacError {
    const val NO_IAC_FILES_CODE = 2114 // NoLoadableInput
    const val INVALID_JSON_FILE_ERROR = 1021
    const val INVALID_YAML_FILE_ERROR = 1022
    const val FAILED_TO_PARSE_INPUT = 2105
    const val NOT_RECOGNIZED_OPTION_ERROR_CODE = 422
    const val COULD_NOT_FIND_VALID_IAC_FILES_ERROR_CODE = 1010
    const val FAILED_TO_PARSE_TERRAFORM = 1040
}
