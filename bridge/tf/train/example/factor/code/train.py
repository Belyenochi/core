import tensorflow as tf
from tftrain import TensorFlowContext, TensorFlowTrainRequest

def train(request: TensorFlowTrainRequest, context: TensorFlowContext):
    input_dataset = request.train_dataset()
    print(input_dataset)

    left = input_dataset.map(lambda a,b: a)
    right = input_dataset.map(lambda a,b: b)

    left_iter = left.batch(1).make_initializable_iterator()
    right_iter = right.batch(1).make_initializable_iterator()

    context.session.run([left_iter.initializer, right_iter.initializer])

    left_next = left_iter.get_next()
    right_next = right_iter.get_next()

    w = tf.Variable(0.5, name="w")
    b_predict = w * left_next
    cost = tf.reduce_sum(tf.squared_difference(b_predict, right_next))
    optimizer = tf.train.GradientDescentOptimizer(0.01).minimize(cost)

    context.session.run(tf.global_variables_initializer())

    for _ in range(50):
        context.session.run([left_iter.initializer, right_iter.initializer])
        try:
            while True:
                context.session.run(optimizer)
        except tf.errors.OutOfRangeError:
            pass

    w_value = context.session.run(w)
    print("Value ", w_value)

    result_dataset = tf.data.Dataset.from_tensors([w_value])

    request.finish_training_with_dataset(result_dataset, "my_export_dir")


if __name__ == '__main__':
    with tf.Session() as sess:
        dataset = tf.data.Dataset.from_tensor_slices(
            (tf.random_uniform([10]), tf.random_uniform([10]))
        )
        context = TensorFlowContext.local(sess)
        request = TensorFlowTrainRequest.local(dataset, sess)
        train(request, context)
