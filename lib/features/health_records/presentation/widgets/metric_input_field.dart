import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/models/health_metric.dart';
import 'metric_badge.dart';

/// Numeric text field for one [HealthMetric], with the metric's colour-coded
/// icon, its unit as a suffix and digit-only input.
class MetricInputField extends StatelessWidget {
  const MetricInputField({
    super.key,
    required this.metric,
    required this.controller,
    required this.validator,
    this.helperText,
    this.textInputAction = TextInputAction.next,
    this.onFieldSubmitted,
    this.focusNode,
    this.maxDigits = 6,
  });

  final HealthMetric metric;
  final TextEditingController controller;
  final FormFieldValidator<String> validator;
  final String? helperText;
  final TextInputAction textInputAction;
  final ValueChanged<String>? onFieldSubmitted;
  final FocusNode? focusNode;
  final int maxDigits;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: Key('field-${metric.name}'),
      controller: controller,
      focusNode: focusNode,
      validator: validator,
      keyboardType: TextInputType.number,
      textInputAction: textInputAction,
      onFieldSubmitted: onFieldSubmitted,
      inputFormatters: [
        FilteringTextInputFormatter.digitsOnly,
        LengthLimitingTextInputFormatter(maxDigits),
      ],
      decoration: InputDecoration(
        labelText: metric.label,
        hintText: '0',
        helperText: helperText,
        suffixText: metric.unit,
        prefixIcon: Padding(
          padding: const EdgeInsets.only(left: 8, right: 8),
          child: MetricIcon(metric, size: 20, padding: 6),
        ),
        prefixIconConstraints: const BoxConstraints(minWidth: 48, minHeight: 40),
      ),
    );
  }
}
