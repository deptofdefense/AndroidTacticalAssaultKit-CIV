#include "pch.h"

#include "util/Tasking.h"

using namespace TAK::Engine::Util;

namespace {
	TAKErr value42(int &result) {
		result = 42;
		return TE_Ok;
	}

	TAKErr add1(int& output, int input) {
		output = input + 1;
		return TE_Ok;
	}

	TAKErr middleErrStep(int &output, int input) {
		return TE_Err;
	}

	TAKErr errorStep(int& output, int input, TAKErr err) {
		return err;
	}

	TAKErr trapOutput90(int &output, TAKErr err) {
		output = 90;
		return TE_Ok;
	}

	TAKErr addStep(int& output, int a, int b) {
		output = a + b;
		return TE_Ok;
	}
}

namespace takenginetests {

	TEST(TaskingTests, testFlexTrapTasks) {
		std::vector<Future<int>> futures;
		for (size_t i = 0; i < 1000; ++i) {
			FutureTask<int> result = Task_begin(GeneralWorkers_flex(), value42)
				.then(add1)
				.then(add1)
				.then(middleErrStep)
				.trap(trapOutput90);
			futures.push_back(result);
		}

		for (Future<int> &f : futures) {
			int v = 0;
			TAKErr err = TE_Err;
			TAKErr code = f.await(v, err);
			ASSERT_TRUE(code == TE_Ok);
			ASSERT_TRUE(err == TE_Ok);
			ASSERT_EQ(v, 90);
		}
	}

	TEST(TaskingTests, test50NewThreadTasksSkipTrap) {
		std::vector<Future<int>> futures;
		for (size_t i = 0; i < 50; ++i) {
			FutureTask<int> result = Task_begin(GeneralWorkers_newThread(), value42)
				.then(add1)
				.then(add1)
				.trap(trapOutput90);
			futures.push_back(result);
		}

		for (Future<int>& f : futures) {
			int v = 0;
			TAKErr err = TE_Err;
			TAKErr code = f.await(v, err);
			ASSERT_TRUE(code == TE_Ok);
			ASSERT_TRUE(err == TE_Ok);
			ASSERT_EQ(v, 44);
		}
	}

	TEST(TaskingTests, testImmediateTrappedArgs) {
		FutureTask<int> f = Task_begin(GeneralWorkers_immediate(), value42)
			.then(addStep, 1);

		int v = 0;
		TAKErr err = TE_Err;
		TAKErr code = f.await(v, err);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(err == TE_Ok);
		ASSERT_EQ(v, 43);
	}

	TEST(TaskingTests, testFutureCapture) {
		FutureTask<int> a = Task_begin(GeneralWorkers_single(), value42);
		FutureTask<int> f = Task_begin(GeneralWorkers_single(), add1, a)
			.then(add1);

		int v = 0;
		TAKErr err = TE_Err;
		TAKErr code = f.await(v, err);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(err == TE_Ok);
		ASSERT_EQ(v, 44);
	}

	TEST(TaskingTests, testChainError) {
		FutureTask<int> f = Task_begin(GeneralWorkers_single(), value42)
			.then(errorStep, TE_InvalidArg)
			.then(add1)
			.then(add1)
			.then(add1);

		int v = 0;
		TAKErr err = TE_Err;
		TAKErr code = f.await(v, err);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(err == TE_InvalidArg);
		ASSERT_EQ(v, 0);
	}

	TEST(TaskingTests, testChainErrorFarTrapWithTail) {
		FutureTask<int> f = Task_begin(GeneralWorkers_single(), value42)
			.then(errorStep, TE_InvalidArg)
			.then(add1)
			.then(add1)
			.then(add1)
			.trap(trapOutput90)
			.then(add1)
			.then(add1);

		int v = 0;
		TAKErr err = TE_Err;
		TAKErr code = f.await(v, err);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(err == TE_Ok);
		ASSERT_EQ(v, 92);
	}
}