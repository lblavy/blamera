"""Décisions aux frontières, prototypes et validation sans modèle natif."""

import math
import unittest

from src.blamera_pc.evaluation_data import Policy, decide, prototypes
from src.blamera_pc.gallery import GalleryError


class EvaluationDataTests(unittest.TestCase):
    def test_policy_rejects_non_finite_boolean_and_invalid_order(self):
        for policy in (
            Policy(True),
            Policy(float("nan")),
            Policy(0.7, 0.6),
            Policy(-2),
            Policy(0.3, 1.1),
            Policy(margin=-0.1),
            Policy(margin=3),
        ):
            with self.subTest(policy=policy), self.assertRaises(GalleryError):
                policy.validate()
        Policy(-1, 1, 2).validate()

    def test_prototypes_average_normalized_samples_per_identity(self):
        actual = prototypes({"a": [(3.0, 0.0), (0.0, 2.0)], "b": [(0.0, -4.0)]}, 2)
        self.assertAlmostEqual(actual["a"][0], math.sqrt(0.5))
        self.assertEqual(actual["b"], (0.0, -1.0))
        for samples in (
            {},
            {"a": []},
            {"a": [(1.0, 0.0), (-1.0, 0.0)]},
            {"a": [(float("inf"), 0.0)]},
            {"a": [(1.0,)]},
        ):
            with self.subTest(samples=samples), self.assertRaises(GalleryError):
                prototypes(samples, 2)

    def test_one_identity_uses_absolute_thresholds_and_no_fake_second(self):
        identities = {"a": (1.0, 0.0)}
        for score, expected in (
            (0.299, "Inconnu"),
            (0.3, "Incertain"),
            (0.599, "Incertain"),
            (0.6, "Reconnu"),
            (1.0, "Reconnu"),
        ):
            decision = decide((score, math.sqrt(1 - score * score)), identities, Policy())
            self.assertEqual(decision.status, expected)
            self.assertEqual(decision.identity, "a" if expected == "Reconnu" else None)
            self.assertIsNone(decision.second)
            self.assertEqual(decision.candidate, "a")

    def test_two_similar_identities_abstain_and_order_is_deterministic(self):
        decision = decide((1.0, 0.0), {"b": (1.0, 0.0), "a": (1.0, 0.0)}, Policy())
        self.assertEqual(decision.status, "Incertain")
        self.assertEqual(decision.candidate, "a")
        self.assertEqual(decision.second, 1.0)
        self.assertEqual(
            decide((1.0, 0.0), {"a": (1.0, 0.0), "b": (0.0, 1.0)}, Policy()).identity, "a"
        )

    def test_empty_or_malformed_identity_spaces_are_errors(self):
        for vector, identities in (
            ((1.0, 0.0), {}),
            ((0.0, 0.0), {"a": (1.0, 0.0)}),
            ((1.0, 0.0), {"a": (1.0,)}),
        ):
            with self.subTest(identities=identities), self.assertRaises(GalleryError):
                decide(vector, identities, Policy())
